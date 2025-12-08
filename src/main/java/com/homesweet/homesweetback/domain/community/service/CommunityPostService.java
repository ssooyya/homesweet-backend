package com.homesweet.homesweetback.domain.community.service;

import com.homesweet.homesweetback.common.exception.ErrorCode;
import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostRequest;
import com.homesweet.homesweetback.domain.community.dto.CommunityPostResponse;
import com.homesweet.homesweetback.domain.community.exception.CommunityException;
import com.homesweet.homesweetback.domain.community.entity.CommunityImageEntity;
import com.homesweet.homesweetback.domain.community.entity.CommunityPostEntity;
import com.homesweet.homesweetback.domain.community.repository.CommunityImageRepository;
import com.homesweet.homesweetback.domain.community.repository.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommunityPostService {

    private final CommunityPostRepository postRepository;
    private final CommunityImageRepository imageRepository;
    private final UserRepository userRepository;
    private final CommunityImageUploader imageUploader;
    private final CommunityCountService communityCountService;

    /**
     * 게시글 작성
     */
    @Transactional
    public CommunityPostResponse createPost(List<MultipartFile> images, CommunityPostRequest request, Long userId) {
        // User 조회
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.USER_NOT_FOUND));

        CommunityPostEntity savedPost = postRepository.save(
                CommunityPostEntity.builder()
                        .author(author)
                        .title(request.title())
                        .content(request.content())
                        .category(request.category())
                        .build()
        );

        // 이미지 업로드 및 저장
        List<String> imageUrls = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            imageUrls = imageUploader.uploadCommunityImages(images);

            for (int i = 0; i < imageUrls.size(); i++) {
                imageRepository.save(
                        CommunityImageEntity.builder()
                                .post(savedPost)
                                .imageUrl(imageUrls.get(i))
                                .imageOrder(i + 1) // 0이 아닌 1부터 시작하도록 수정
                                .build()
                );
            }
        }

        return CommunityPostResponse.from(savedPost, imageUrls);
    }

    /**
     * 게시글 단건 조회 - Cache-Aside 패턴 적용
     */
    public CommunityPostResponse getPost(Long postId) {
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        // 이미지 조회
        List<String> imageUrls = imageRepository.findByPostOrderByImageOrderAsc(post)
                .stream()
                .map(CommunityImageEntity::getImageUrl)
                .toList();

        // Redis에서 실시간 카운터 조회 (Cache-Aside)
        Integer viewCount = communityCountService.getViewCountFromCache(postId);
        Integer likeCount = communityCountService.getLikeCountFromCache(postId);
        Integer commentCount = communityCountService.getCommentCountFromCache(postId);

        return CommunityPostResponse.fromWithCachedCounts(post, imageUrls, viewCount, likeCount, commentCount);
    }

    /**
     * 게시글 수정
     */
    @Transactional
    public CommunityPostResponse updatePost(Long postId, CommunityPostRequest request, Long userId) {
        // 게시글 조회
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        // 작성자 본인 확인
        if (!post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_POST_FORBIDDEN);
        }

        // 게시글 수정
        post.updatePost(request.title(), request.content(), request.category());

        // 이미지 조회
        List<String> imageUrls = imageRepository.findByPostOrderByImageOrderAsc(post)
                .stream()
                .map(CommunityImageEntity::getImageUrl)
                .toList();

        return CommunityPostResponse.from(post, imageUrls);
    }

    /**
     * 게시글 삭제 (소프트 삭제)
     */
    @Transactional
    public void deletePost(Long postId, Long userId) {
        // 게시글 조회
        CommunityPostEntity post = postRepository.findByPostIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        // 작성자 본인 확인
        if (!post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMUNITY_POST_FORBIDDEN);
        }

        // 게시글 소프트 삭제
        post.deletePost();
    }

    /**
     * 게시글 목록 조회 (페이지네이션) - Cache-Aside 패턴 적용
     */
    public Page<CommunityPostResponse> getPosts(Pageable pageable) {
        Page<CommunityPostEntity> postsPage = postRepository.findByIsDeletedFalse(pageable);
        List<CommunityPostEntity> posts = postsPage.getContent();

        if (posts.isEmpty()) {
            return postsPage.map(post -> CommunityPostResponse.from(post, null));
        }
        List<CommunityImageEntity> allImages = imageRepository.findAllByPostInOrderByPostPostIdAscImageOrderAsc(posts);

        Map<Long, List<String>> postImagesMap = allImages.stream()
                .collect(Collectors.groupingBy(
                        image -> image.getPost().getPostId(),
                        Collectors.mapping(CommunityImageEntity::getImageUrl, Collectors.toList())
                ));

        return postsPage.map(post -> {
            List<String> imageUrls = postImagesMap.getOrDefault(post.getPostId(), List.of());

            // Redis에서 실시간 카운터 조회 (Cache-Aside)
            Integer viewCount = communityCountService.getViewCountFromCache(post.getPostId());
            Integer likeCount = communityCountService.getLikeCountFromCache(post.getPostId());
            Integer commentCount = communityCountService.getCommentCountFromCache(post.getPostId());

            return CommunityPostResponse.fromWithCachedCounts(post, imageUrls, viewCount, likeCount, commentCount);
        });
    }
}
