package com.batteryhealth.app.ui.onboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.batteryhealth.app.R;

/**
 * 引导页 ViewPager2 适配器。
 */
public class OnboardPagerAdapter extends RecyclerView.Adapter<OnboardPagerAdapter.VH> {

    private final int[] titles;
    private final int[] subtitles;

    public OnboardPagerAdapter(int[] titles, int[] subtitles) {
        this.titles = titles;
        this.subtitles = subtitles;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.tvTitle.setText(titles[position]);
        holder.tvSubtitle.setText(subtitles[position]);
    }

    @Override
    public int getItemCount() { return titles.length; }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvSubtitle;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.onboard_item_title);
            tvSubtitle = itemView.findViewById(R.id.onboard_item_subtitle);
        }
    }
}
