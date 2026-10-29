package com.example.safesphere.history;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safesphere.R;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.utils.StatusUi;
import com.example.safesphere.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    public interface OnEventClick {
        void onClick(SafetyEvent event);
    }

    private final List<SafetyEvent> items = new ArrayList<>();
    private final OnEventClick onClick;

    public HistoryAdapter(OnEventClick onClick) {
        this.onClick = onClick;
    }

    public void submit(List<SafetyEvent> events) {
        items.clear();
        items.addAll(events);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SafetyEvent e = items.get(position);
        bind(holder.itemView, e);
        holder.itemView.setOnClickListener(v -> onClick.onClick(e));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** Shared with the Home screen's recent-activity list. */
    public static void bind(View item, SafetyEvent e) {
        Context c = item.getContext();
        boolean sos = SafetyEvent.TYPE_SOS.equals(e.type);
        ImageView icon = item.findViewById(R.id.ivIcon);
        icon.setImageResource(StatusUi.eventIcon(e));
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c,
                sos ? R.color.emergency_red_text : R.color.primary_dark)));
        item.findViewById(R.id.iconTile).setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(c, sos ? R.color.emergency_red_light : R.color.primary_light)));

        ((TextView) item.findViewById(R.id.tvTitle)).setText(StatusUi.eventTitle(c, e));
        String meta = TimeUtils.formatDateTime(c, e.startedAt);
        if (sos) meta = c.getString(R.string.history_meta_with_trigger, meta, StatusUi.triggerLabel(c, e.trigger));
        ((TextView) item.findViewById(R.id.tvMeta)).setText(meta);

        TextView location = item.findViewById(R.id.tvLocation);
        String summary = StatusUi.locationSummary(c, e);
        location.setVisibility(summary == null ? View.GONE : View.VISIBLE);
        location.setText(summary);

        StatusUi.applyEventStatus(item.findViewById(R.id.tvChip), e);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
