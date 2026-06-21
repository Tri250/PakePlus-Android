package com.batteryhealth.app.ui.community;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.batteryhealth.app.R;
import com.batteryhealth.app.utils.UiAnimationHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 电池江湖 - 社区分享与交流页面
 *
 * 功能：
 * 1. 展示电池养护小贴士
 * 2. 本地帖子列表（分享电池使用心得、保养技巧）
 * 3. 发布新帖子
 * 4. 点赞互动
 */
public class CommunityFragment extends Fragment {
    private static final String TAG = "CommunityFragment";
    private static final String PREFS_COMMUNITY = "community_prefs";
    private static final String KEY_POSTS = "posts";

    private RecyclerView rvPosts;
    private PostAdapter postAdapter;
    private TextView tvEmptyPosts;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_community, container, false);
            initViews(view);
            loadPosts();
            return view;
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage(), e);
            return createErrorView(e);
        }
    }

    private void initViews(View view) {
        rvPosts = view.findViewById(R.id.rv_posts);
        tvEmptyPosts = view.findViewById(R.id.tv_empty_posts);
        FloatingActionButton fabAdd = view.findViewById(R.id.fab_add_post);

        rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        postAdapter = new PostAdapter();
        rvPosts.setAdapter(postAdapter);

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> showAddPostDialog());
        }

        // 点赞回调
        postAdapter.setOnLikeListener((position, post) -> {
            post.likes++;
            savePosts(postAdapter.getPosts());
            postAdapter.notifyItemChanged(position);
        });
    }

    private void loadPosts() {
        List<Post> posts = readPostsFromPrefs();
        if (posts.isEmpty()) {
            // 初始化默认帖子
            posts.add(new Post("电池健康度95%以上的秘诀", "我坚持每次充电到80%就拔掉，用了两年健康度还有96%！建议大家开启智能充电模式。", "电池达人", System.currentTimeMillis() - 86400000L * 2, 12));
            posts.add(new Post("夏天充电要注意", "最近天气热，充电时温度经常到42度，我晚上开空调充，温度降到35度左右，对电池好很多。", "数码爱好者", System.currentTimeMillis() - 86400000L * 1, 8));
            posts.add(new Post("循环次数和健康度的关系", "我的手机循环了400多次，健康度还有89%，感觉还能再战一年。大家循环多少次了？", "科技迷", System.currentTimeMillis() - 3600000L * 5, 5));
            savePosts(posts);
        }
        postAdapter.setData(posts);
        updateEmptyView(posts);
    }

    private void updateEmptyView(List<Post> posts) {
        if (tvEmptyPosts != null) {
            tvEmptyPosts.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showAddPostDialog() {
        Context context = requireContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        EditText etTitle = new EditText(context);
        etTitle.setHint("标题");
        layout.addView(etTitle);

        EditText etContent = new EditText(context);
        etContent.setHint("分享你的电池使用心得…");
        etContent.setMinLines(4);
        layout.addView(etContent);

        new AlertDialog.Builder(context)
                .setTitle("发布帖子")
                .setView(layout)
                .setPositiveButton("发布", (dialog, which) -> {
                    String title = etTitle.getText().toString().trim();
                    String content = etContent.getText().toString().trim();
                    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(content)) {
                        Toast.makeText(context, "标题和内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Post post = new Post(title, content, "我", System.currentTimeMillis(), 0);
                    List<Post> posts = postAdapter.getPosts();
                    posts.add(0, post);
                    savePosts(posts);
                    postAdapter.setData(posts);
                    updateEmptyView(posts);
                    rvPosts.scrollToPosition(0);
                    Toast.makeText(context, "发布成功", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private List<Post> readPostsFromPrefs() {
        List<Post> list = new ArrayList<>();
        Context ctx = getContext();
        if (ctx == null) return list;
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_COMMUNITY, Context.MODE_PRIVATE);
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

    private void savePosts(List<Post> posts) {
        Context ctx = getContext();
        if (ctx == null) return;
        try {
            JSONArray array = new JSONArray();
            for (Post post : posts) {
                array.put(post.toJson());
            }
            ctx.getSharedPreferences(PREFS_COMMUNITY, Context.MODE_PRIVATE)
                    .edit().putString(KEY_POSTS, array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save posts", e);
        }
    }

    private View createErrorView(Exception e) {
        Context ctx = getContext();
        if (ctx == null) return new View(requireContext());
        TextView errorView = new TextView(ctx);
        String message = getString(R.string.error_view_load_failed, e.getClass().getSimpleName(), e.getMessage());
        errorView.setText(message);
        errorView.setTextColor(ContextCompat.getColor(ctx, R.color.label));
        errorView.setTextSize(16);
        errorView.setPadding(40, 100, 40, 40);
        errorView.setBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_canvas));
        return errorView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            UiAnimationHelper.animateCardsEntry(view);
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
        }
    }

    // ===== Post Model =====
    public static class Post {
        public String title;
        public String content;
        public String author;
        public long timestamp;
        public int likes;

        public Post(String title, String content, String author, long timestamp, int likes) {
            this.title = title;
            this.content = content;
            this.author = author;
            this.timestamp = timestamp;
            this.likes = likes;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("title", title);
            obj.put("content", content);
            obj.put("author", author);
            obj.put("timestamp", timestamp);
            obj.put("likes", likes);
            return obj;
        }

        public static Post fromJson(JSONObject obj) {
            return new Post(
                    obj.optString("title"),
                    obj.optString("content"),
                    obj.optString("author", "匿名"),
                    obj.optLong("timestamp"),
                    obj.optInt("likes", 0)
            );
        }
    }

    // ===== Post Adapter =====
    public static class PostAdapter extends RecyclerView.Adapter<PostAdapter.ViewHolder> {
        private final List<Post> data = new ArrayList<>();
        private OnLikeListener likeListener;
        private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        public interface OnLikeListener {
            void onLike(int position, Post post);
        }

        public void setOnLikeListener(OnLikeListener listener) {
            this.likeListener = listener;
        }

        public void setData(List<Post> list) {
            data.clear();
            data.addAll(list);
            notifyDataSetChanged();
        }

        public List<Post> getPosts() {
            return new ArrayList<>(data);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Post post = data.get(position);
            holder.tvTitle.setText(post.title);
            holder.tvContent.setText(post.content);
            holder.tvAuthor.setText(post.author);
            holder.tvTime.setText(sdf.format(new Date(post.timestamp)));
            holder.tvLikes.setText(String.valueOf(post.likes));
            holder.btnLike.setOnClickListener(v -> {
                if (likeListener != null) {
                    likeListener.onLike(holder.getAdapterPosition(), post);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvContent, tvAuthor, tvTime, tvLikes;
            View btnLike;

            ViewHolder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_post_title);
                tvContent = itemView.findViewById(R.id.tv_post_content);
                tvAuthor = itemView.findViewById(R.id.tv_post_author);
                tvTime = itemView.findViewById(R.id.tv_post_time);
                tvLikes = itemView.findViewById(R.id.tv_post_likes);
                btnLike = itemView.findViewById(R.id.btn_like);
            }
        }
    }
}
