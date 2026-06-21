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
import com.batteryhealth.app.community.CommunityRepository;
import com.batteryhealth.app.community.LocalCommunityRepository;
import com.batteryhealth.app.utils.StateLayoutHelper;
import com.batteryhealth.app.utils.UiAnimationHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * 电池江湖 - 社区分享与交流页面
 *
 * 功能：
 * 1. 展示电池养护小贴士
 * 2. 本地帖子列表（分享电池使用心得、保养技巧）
 * 3. 发布新帖子（匿名昵称）
 * 4. 点赞互动
 * 5. 排序切换（最新 / 最热）
 * 6. 可替换后端仓库层
 */
public class CommunityFragment extends Fragment {
    private static final String TAG = "CommunityFragment";
    private static final String PREFS_COMMUNITY = "community_prefs";
    private static final String KEY_ANONYMOUS_ID = "anonymous_nickname";

    private static final String[] NICKNAME_PREFIXES = {
            "电池侠", "充电达人", "续航高手", "省电大师", "电池守护者",
            "充电小能手", "电池观察员", "续航先锋", "省电达人", "电池知己"
    };

    private RecyclerView rvPosts;
    private PostAdapter postAdapter;
    private TextView tvEmptyPosts;
    private TextView tvSortNewest;
    private TextView tvSortHottest;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private StateLayoutHelper stateLayoutHelper;

    private CommunityRepository repository;
    private List<Post> allPosts = new ArrayList<>();
    private SortMode currentSort = SortMode.NEWEST;

    private enum SortMode {
        NEWEST, HOTTEST
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 默认使用本地仓库，未来可在此处切换为 RemoteCommunityRepository
        if (getContext() != null) {
            repository = new LocalCommunityRepository(getContext());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.fragment_community, container, false);
            initViews(view);
            // 初始化 StateLayoutHelper
            if (view instanceof ViewGroup) {
                ViewGroup scrollChild = (ViewGroup) view;
                if (scrollChild.getChildCount() > 0 && scrollChild.getChildAt(0) instanceof ViewGroup) {
                    stateLayoutHelper = new StateLayoutHelper((ViewGroup) scrollChild.getChildAt(0));
                    stateLayoutHelper.showLoading(null);
                }
            }
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
        tvSortNewest = view.findViewById(R.id.tv_sort_newest);
        tvSortHottest = view.findViewById(R.id.tv_sort_hottest);
        FloatingActionButton fabAdd = view.findViewById(R.id.fab_add_post);

        Context ctx = getContext();
        if (ctx == null) return;
        rvPosts.setLayoutManager(new LinearLayoutManager(ctx));
        postAdapter = new PostAdapter();
        rvPosts.setAdapter(postAdapter);

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> showAddPostDialog());
        }

        // 排序切换
        if (tvSortNewest != null) {
            tvSortNewest.setOnClickListener(v -> {
                if (currentSort != SortMode.NEWEST) {
                    currentSort = SortMode.NEWEST;
                    updateSortTabs();
                    applySortAndRefresh();
                }
            });
        }
        if (tvSortHottest != null) {
            tvSortHottest.setOnClickListener(v -> {
                if (currentSort != SortMode.HOTTEST) {
                    currentSort = SortMode.HOTTEST;
                    updateSortTabs();
                    applySortAndRefresh();
                }
            });
        }

        updateSortTabs();

        // 点赞回调
        postAdapter.setOnLikeListener((position, post) -> {
            if (repository != null) {
                repository.likePost(post.postId, success -> {
                    if (isAdded() && getActivity() != null) {
                        mainHandler.post(() -> {
                            // 重新加载数据以反映点赞变化
                            reloadPosts();
                        });
                    }
                });
            }
        });
    }

    private void updateSortTabs() {
        Context ctx = getContext();
        if (ctx == null) return;

        if (tvSortNewest != null) {
            tvSortNewest.setSelected(currentSort == SortMode.NEWEST);
            tvSortNewest.setTextColor(currentSort == SortMode.NEWEST
                    ? ContextCompat.getColor(ctx, R.color.coloros_green)
                    : ContextCompat.getColor(ctx, R.color.label_3));
        }
        if (tvSortHottest != null) {
            tvSortHottest.setSelected(currentSort == SortMode.HOTTEST);
            tvSortHottest.setTextColor(currentSort == SortMode.HOTTEST
                    ? ContextCompat.getColor(ctx, R.color.coloros_orange)
                    : ContextCompat.getColor(ctx, R.color.label_3));
        }
    }

    private void applySortAndRefresh() {
        List<Post> sorted = new ArrayList<>(allPosts);
        switch (currentSort) {
            case HOTTEST:
                Collections.sort(sorted, (a, b) -> Integer.compare(b.likes, a.likes));
                break;
            case NEWEST:
            default:
                Collections.sort(sorted, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                break;
        }
        postAdapter.setData(sorted);
        updateEmptyView(sorted);
    }

    private void loadPosts() {
        if (getContext() == null) return;
        if (repository == null) {
            repository = new LocalCommunityRepository(getContext());
        }
        try {
            allPosts = repository.loadPosts();
            if (allPosts.isEmpty()) {
                // 初始化默认帖子
                allPosts.add(new Post("电池健康度95%以上的秘诀", "我坚持每次充电到80%就拔掉，用了两年健康度还有96%！建议大家开启智能充电模式。", "电池达人", System.currentTimeMillis() - 86400000L * 2, 12));
                allPosts.add(new Post("夏天充电要注意", "最近天气热，充电时温度经常到42度，我晚上开空调充，温度降到35度左右，对电池好很多。", "数码爱好者", System.currentTimeMillis() - 86400000L * 1, 8));
                allPosts.add(new Post("循环次数和健康度的关系", "我的手机循环了400多次，健康度还有89%，感觉还能再战一年。大家循环多少次了？", "科技迷", System.currentTimeMillis() - 3600000L * 5, 5));
                repository.savePosts(allPosts);
            }
            applySortAndRefresh();
            // 数据加载完成，显示内容
            if (stateLayoutHelper != null && !allPosts.isEmpty()) {
                stateLayoutHelper.showContent();
            } else if (stateLayoutHelper != null && allPosts.isEmpty()) {
                stateLayoutHelper.showEmpty(getString(R.string.state_empty_posts), R.drawable.ic_community,
                        v -> showAddPostDialog(), getString(R.string.action_post));
            }
        } catch (Exception e) {
            if (stateLayoutHelper != null) {
                stateLayoutHelper.showError(getString(R.string.state_error_posts), v -> loadPosts());
            }
        }
    }

    private void reloadPosts() {
        if (repository == null || getContext() == null) return;
        try {
            allPosts = repository.loadPosts();
            applySortAndRefresh();
            if (stateLayoutHelper != null) {
                if (allPosts.isEmpty()) {
                    stateLayoutHelper.showEmpty(getString(R.string.state_empty_posts), R.drawable.ic_community,
                            v -> showAddPostDialog(), getString(R.string.action_post));
                } else {
                    stateLayoutHelper.showContent();
                }
            }
        } catch (Exception e) {
            if (stateLayoutHelper != null) {
                stateLayoutHelper.showError(getString(R.string.state_error_posts), v -> reloadPosts());
            }
        }
    }

    private void updateEmptyView(List<Post> posts) {
        if (tvEmptyPosts != null) {
            tvEmptyPosts.setVisibility(posts.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 获取或生成用户的匿名昵称，持久化到 SharedPreferences
     */
    private String getAnonymousNickname() {
        Context ctx = getContext();
        if (ctx == null) return "匿名用户";
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_COMMUNITY, Context.MODE_PRIVATE);
        String nickname = prefs.getString(KEY_ANONYMOUS_ID, null);
        if (nickname == null) {
            Random random = new Random();
            String prefix = NICKNAME_PREFIXES[random.nextInt(NICKNAME_PREFIXES.length)];
            int suffix = 1000 + random.nextInt(9000);
            nickname = prefix + "_" + suffix;
            prefs.edit().putString(KEY_ANONYMOUS_ID, nickname).apply();
        }
        return nickname;
    }

    private void showAddPostDialog() {
        Context context = getContext();
        if (context == null) return;

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
                    String author = getAnonymousNickname();
                    Post post = new Post(title, content, author, System.currentTimeMillis(), 0);
                    if (repository != null) {
                        repository.addPost(post);
                    }
                    reloadPosts();
                    if (rvPosts != null) {
                        rvPosts.scrollToPosition(0);
                    }
                    Toast.makeText(context, "发布成功", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private View createErrorView(Exception e) {
        Context ctx = getContext();
        if (ctx == null) {
            // 如果 Fragment 已脱离 Activity，返回一个空白 View 避免崩溃
            return new View(requireActivity());
        }
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
        public String postId;
        public String title;
        public String content;
        public String author;
        public long timestamp;
        public int likes;

        public Post(String title, String content, String author, long timestamp, int likes) {
            this.postId = UUID.randomUUID().toString();
            this.title = title;
            this.content = content;
            this.author = author;
            this.timestamp = timestamp;
            this.likes = likes;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("postId", postId);
            obj.put("title", title);
            obj.put("content", content);
            obj.put("author", author);
            obj.put("timestamp", timestamp);
            obj.put("likes", likes);
            return obj;
        }

        public static Post fromJson(JSONObject obj) {
            Post post = new Post(
                    obj.optString("title"),
                    obj.optString("content"),
                    obj.optString("author", "匿名"),
                    obj.optLong("timestamp"),
                    obj.optInt("likes", 0)
            );
            // 恢复原始 postId，如果没有则保留自动生成的
            String existingId = obj.optString("postId", null);
            if (existingId != null && !existingId.isEmpty()) {
                post.postId = existingId;
            }
            return post;
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
