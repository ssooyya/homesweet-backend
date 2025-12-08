package com.homesweet.homesweetback.domain.chat.service.Imp;

import com.homesweet.homesweetback.common.exception.BusinessException;
import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.common.s3.ImageUploader;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.chat.dto.response.IndividualRoomCreateResponse;
import com.homesweet.homesweetback.domain.chat.dto.request.CreateGroupRoomRequest;
import com.homesweet.homesweetback.domain.chat.dto.response.*;
import com.homesweet.homesweetback.domain.chat.entity.ChatMessage;
import com.homesweet.homesweetback.domain.chat.entity.ChatRoom;
import com.homesweet.homesweetback.domain.chat.entity.RoomMember;
import com.homesweet.homesweetback.domain.chat.entity.enums.ChatRoomType;
import com.homesweet.homesweetback.domain.chat.entity.enums.ChatUserRole;
import com.homesweet.homesweetback.domain.chat.event.ChatRoomEventPublisher;
import com.homesweet.homesweetback.domain.chat.mapper.ChatRoomMapper;
import com.homesweet.homesweetback.domain.chat.repository.jpa.ChatMessageRepository;
import com.homesweet.homesweetback.domain.chat.repository.jpa.ChatRoomRepository;

import com.homesweet.homesweetback.domain.chat.repository.jpa.RoomMemberRepository;
import com.homesweet.homesweetback.domain.chat.service.ChatRoomService;
import com.homesweet.homesweetback.domain.chat.service.RoomMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMapper chatRoomMapper;
    private final ImageUploader s3ImageUploader;
    private final RoomMemberService roomMemberService;
    private final ChatRoomEventPublisher chatRoomEventPublisher;

    /**
     * 개인 채팅방 생성
     */
    @Override
    @Transactional
    public IndividualRoomCreateResponse createOrGetIndividualRoom(Long meId, Long targetId) {

        String pairKey = roomMemberService.buildPairKey(meId, targetId);
        Optional<ChatRoom> existing = chatRoomRepository.findByTypeAndPairKey(ChatRoomType.INDIVIDUAL, pairKey);

        // 기존 방이 있다면 재사용
        if (existing.isPresent()) {
            ChatRoom chatRoom = existing.get();
            return IndividualRoomCreateResponse.builder()
                    .roomId(chatRoom.getId())
                    .reused(true)
                    .build();
        }
        // 방 생성
        ChatRoom room = ChatRoom.builder()
                .type(ChatRoomType.INDIVIDUAL)
                .name("INDIVIDUAL-" + pairKey)
                .pairKey(pairKey)
                .build();
        chatRoomRepository.saveAndFlush(room);

        // 멤버 저장
        roomMemberService.registerIndividualMember(room, meId, targetId);

        return IndividualRoomCreateResponse.builder()
                .roomId(room.getId())
                .type(room.getType().name())
                .name(room.getName())
                .pairKey(room.getPairKey())
                .reused(false)
                .build();
    }


    /**
     * 그룹 채팅방 생성
     */
    @Override
    @Transactional
    public GroupRoomCreateResponse createGroupRoom(Long ownerId, CreateGroupRoomRequest request) {

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String thumbnailUrl = s3ImageUploader.upload(
                request.roomThumbnailUrl(),
                "group/chat/thumbnail"
        );

        // 방 타입 확인: 그룹방이 아니면 예외 처리
        if (!request.roomType().equals(ChatRoomType.GROUP)) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_TYPE);
        }
        // 요청 받아온 dto를 엔터티 객체로 생성
        ChatRoom chatRoom = chatRoomMapper.toEntity(request, thumbnailUrl);

        // 채팅방 정보 저장
        chatRoomRepository.saveAndFlush(chatRoom);

        // 방장 정보 저장
        RoomMember roomOwner = RoomMember.createMember(chatRoom, owner, ChatUserRole.OWNER);
        roomMemberRepository.save(roomOwner);

        // 저장된 정보 응답
        return chatRoomMapper.toDto(chatRoom, ownerId);
    }


    /**
     * 개인 채팅방 상세 조회
     */
    @Override
    @Transactional(readOnly = true)
    public IndividualChatDetailResponse getIndividualChatDetail(Long userId, Long roomId) {

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        if (!chatRoom.getType().equals(ChatRoomType.INDIVIDUAL)) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_TYPE);
        }

        User partner = roomMemberRepository.findPartnerUserInRoom(userId, roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_MEMBER_NOT_FOUND));


        // 3. 응답 DTO 생성
        return IndividualChatDetailResponse.builder()
                .roomId(roomId)
                .partnerId(partner.getId())
                .partnerName(partner.getName())
                .partnerProfileImageUrl(partner.getProfileImageUrl())
                .build();
    }

    /**
     * 그룹 채팅방 단순 조회 (GET - groupDetail)
     */
    @Override
    @Transactional(readOnly = true)
    public GroupChatDetailResponse getGroupChatDetail(Long userId, Long roomId) {

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        if (!chatRoom.getType().equals(ChatRoomType.GROUP)) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_TYPE);
        }

        // 1. 퇴장하지 않은 모든 활성 멤버 조회
        List<RoomMember> activeMembers = roomMemberRepository.findByRoom_IdAndIsExitFalse(roomId);

        // 2. 참여자 User 정보를 DTO List로 변환
        List<RoomMemberResponse> participants = activeMembers.stream()
                .map(member -> RoomMemberResponse.builder()
                        .userId(member.getUser().getId())
                        .userName(member.getUser().getName())
                        .profileUrl(member.getUser().getProfileImageUrl())
                        .build())
                .toList();

        // 4. 응답 DTO 생성 및 반환
        return GroupChatDetailResponse.builder()
                .roomId(roomId)
                .roomName(chatRoom.getName())
                .roomThumbnailUrl(chatRoom.getThumbnailUrl())
                .memberCount(participants.size())
                .participants(participants)
                .roomType(ChatRoomType.GROUP)
                .build();
    }

    /**
     * 채팅방 입장 (Post - groupJoin)
     * 조건 : 1) 신규 입장 (roomMember INSERT)
     *       2) 재입장 (is_exit = ture -> false UPDATE)
     */
    @Override
    @Transactional
    public JoinRoomResponse joinRoom(Long roomId, Long userId) {
        // 1. 채팅방 존재 확인
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        // 2. 기존 멤버십 확인
        Optional<RoomMember> memberOptional = roomMemberRepository.findByRoomIdAndUserId(roomId, userId);

        RoomMemberResponse memberResponse;
        JoinType joinType;

        if (memberOptional.isEmpty()) {
            // 신규 입장
            memberResponse = roomMemberService.registerNewMember(roomId, userId, ChatUserRole.MEMBER);
            joinType = JoinType.NEW_MEMBER;

            chatRoomEventPublisher.publishMemberJoinedEvent(roomId, memberResponse);

        } else if (memberOptional.get().isExit()) {
            // 재입장
            memberResponse = roomMemberService.rejoinMember(roomId, userId);
            joinType = JoinType.REJOIN;

            chatRoomEventPublisher.publishMemberJoinedEvent(roomId, memberResponse);

        } else {
            // 이미 활성 (브로드캐스트 X)
            memberResponse = RoomMemberResponse.from(memberOptional.get());
            joinType = JoinType.ALREADY_JOINED;
        }

        // 4. 응답
        return new JoinRoomResponse(
                chatRoom.getId(),
                chatRoom.getName(),
                List.of(memberResponse),
                List.of(joinType)
        );
    }

    /**
         * 채팅방 퇴장 (사용자 입장)
         */
        @Override
        @Transactional
        public void leaveRoom (Long userId, Long roomId){

            ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

            RoomMember member = roomMemberRepository
                    .findByRoomIdAndUserId(roomId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_MEMBER_NOT_FOUND));

            if (member.isExit()) {
                return; // 이미 퇴장
            }

            // 퇴장 처리
            member.exit();

            Map<String, Object> exitData = Map.of(
                    "userId", userId,
                    "userName", member.getUser().getName()
            );

            chatRoomEventPublisher.publishMemberLeftEvent(roomId, exitData);


            log.info("멤버 퇴장. roomId={}, userId={}", roomId, userId);
        }

        @Override
        public boolean isUserInRoom (Long roomId, Long userId){
            return roomMemberRepository.existsByRoom_IdAndUser_IdAndIsExitFalse(roomId, userId);
        }

        /**
         * 내가 속한 1:1 채팅방 목록 조회
         */
        @Override
        @Transactional(readOnly = true)
        public List<IndividualRoomListResponse> findMyIndividualRooms (Long userId){
            return roomMemberRepository.findMyIndividualRoomList(userId);
        }

        /**
         * 내가 속한 그룹 채팅방 목록 조회
         */
        @Override
        @Transactional(readOnly = true)
        public List<GroupRoomListResponse> findMyGroupRooms (Long userId){
            return roomMemberRepository.findMyGroupRoomList(userId);
        }

        @Override
        @Transactional(readOnly = true)
        public List<GroupRoomListResponse> getAllGroupRooms () {
            List<ChatRoom> groupRooms = chatRoomRepository.findByType(ChatRoomType.GROUP);

            return groupRooms.stream()
                    .map(room -> {
                        // 마지막 메시지 조회
                        ChatMessage lastMessage = chatMessageRepository
                                .findTopByRoomOrderBySentAtDesc(room)
                                .orElse(null);

                        // 방 참여자 수 계산
                        Long memberCount = roomMemberRepository.countByRoomId(room.getId());

                        // 매퍼로 DTO 변환
                        return chatRoomMapper.toGroupRoomListDto(room, lastMessage, memberCount);
                    })
                    .toList();
        }
    }