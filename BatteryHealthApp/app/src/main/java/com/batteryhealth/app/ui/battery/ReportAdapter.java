package com.batteryhealth.app.ui.battery;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.batteryhealth.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 报告页面每日统计列表适配器
 */
public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {

    private final List<DailyStat> data = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM月dd日", Locale.getDefault());

    public void setData(List<DailyStat> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report_daily, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailyStat stat = data.get(position);
        holder.tvDate.setText(dateFormat.format(new Date(stat.timestamp)));
        holder.tvHealth.setText(String.format(Locale.getDefault(), "%.1f%%", stat.avgHealth));
        holder.tvTemp.setText(String.format(Locale.getDefault(), "%.1f°C", stat.avgTemp));
        holder.tvCycles.setText(stat.cycleCount > 0 ? String.valueOf(stat.cycleCount) : "--");

        int healthColor;
        if (stat.avgHealth >= 90) {
            healthColor = R.color.coloros_green;
        } else if (stat.avgHealth >= 80) {
            healthColor = R.color.coloros_yellow;
        } else {
            healthColor = R.color.coloros_orange;
        }
        holder.tvHealth.setTextColor(holder.itemView.getContext().getColor(healthColor));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvHealth, tvTemp, tvCycles;

        ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvHealth = itemView.findViewById(R.id.tv_health);
            tvTemp = itemView.findViewById(R.id.tv_temp);
            tvCycles = itemView.findViewById(R.id.tv_cycles);
        }
    }

    public static class DailyStat {
        public long timestamp;
        public float avgHealth;
        public float avgTemp;
        public int cycleCount;

        public DailyStat(long timestamp, float avgHealth, float avgTemp, int cycleCount) {
            this.timestamp = timestamp;
            this.avgHealth = avgHealth;
            this.avgTemp = avgTemp;
            this.cycleCount = cycleCount;
        }
    }
}
