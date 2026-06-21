package com.batteryhealth.app.community;

import com.batteryhealth.app.ui.community.CommunityFragment.Post;

import java.util.List;

/**
 * 社区数据仓库接口 - 可替换后端层
 *
 * 默认使用 LocalCommunityRepository（SharedPreferences），
 * 未来可切换为 RemoteCommunityRepository（远程后端）。
 */
public interface CommunityRepository {

    /**
     * 加载所有帖子
     */
    List<Post> loadPosts();

    /**
     * 保存帖子列表
     */
    void savePosts(List<Post> posts);

    /**
     * 添加新帖子
     */
    void addPost(Post post);

    /**
     * 点赞帖子
     *
     * @param postId   帖子ID
     * @param listener 完成回调（可为 null）
     */
    void likePost(String postId, OnCompleteListener listener);

    /**
     * 操作完成回调
     */
    interface OnCompleteListener {
        void onComplete(boolean success);
    }
}
