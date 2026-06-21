package com.batteryhealth.app.ui.onboarding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.batteryhealth.app.R;
import java.util.List;

class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.PageViewHolder> {

    private final android.content.Context context;
    private final List<OnboardingPage> pages;

    OnboardingAdapter(android.content.Context context, List<OnboardingPage> pages) {
        this.context = context;
        this.pages = pages;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_onboarding_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        OnboardingPage page = pages.get(position);
        holder.tvTitle.setText(page.title);
        holder.tvDesc.setText(page.description);
        holder.ivIcon.setImageResource(page.iconRes);
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class PageViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDesc;
        ImageView ivIcon;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_onboarding_title);
            tvDesc = itemView.findViewById(R.id.tv_onboarding_desc);
            ivIcon = itemView.findViewById(R.id.iv_onboarding_icon);
        }
    }
}