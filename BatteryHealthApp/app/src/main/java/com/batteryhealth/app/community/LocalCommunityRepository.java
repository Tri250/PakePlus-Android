package com.batteryhealth.app.community;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.batteryhealth.app.ui.community.CommunityFragment.Post;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地社区仓库 - 基于 SharedPreferences 实现
 * <p>
 * 作为默认后端，将帖子数据持久化到本地存储。
 */
public class LocalCommunityRepository implements CommunityRepository {

    private static final String TAG = "LocalCommunityRepo";
    private static final String PREFS_NAME = "community_prefs";
    private static final String KEY_POSTS = "posts";

    private final Context context;

    public LocalCommunityRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public List<Post> loadPosts() {
        List<Post> list = new ArrayList<>();
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_POSTS, "[]");
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(Post.fromJson(obj));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse posts", e);
        }
        return list;
    }

    @Override
    public void savePosts(List<Post> posts) {
        try {
            JSONArray array = new JSONArray();
            for (Post post : posts) {
                array.put(post.toJson());
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_POSTS, array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save posts", e);
        }
    }

    @Override
    public void addPost(Post post) {
        List<Post> posts = loadPosts();
        posts.add(0, post);
        savePosts(posts);
    }

    @Override
    public void likePost(String postId, OnCompleteListener listener) {
        List<Post> posts = loadPosts();
        boolean found = false;
        for (Post post : posts) {
            if (postId.equals(post.postId)) {
                post.likes++;
                found = true;
                break;
            }
        }
        if (found) {
            savePosts(posts);
        }
        if (listener != null) {
            listener.onComplete(found);
        }
    }
}
