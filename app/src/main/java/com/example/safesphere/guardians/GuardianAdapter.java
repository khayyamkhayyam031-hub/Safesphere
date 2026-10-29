package com.example.safesphere.guardians;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safesphere.R;
import com.example.safesphere.data.Guardian;
import com.example.safesphere.utils.StatusUi;

import java.util.ArrayList;
import java.util.List;

/** Guardian cards, with a header row (title, local-only note, empty state) at position 0. */
public class GuardianAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onEdit(Guardian g);

        void onRemove(Guardian g);

        void onBindHeader(View header, int guardianCount);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Guardian> items = new ArrayList<>();
    private final Listener listener;

    public GuardianAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Guardian> guardians) {
        items.clear();
        items.addAll(guardians);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        int layout = viewType == TYPE_HEADER ? R.layout.view_guardians_header : R.layout.item_guardian;
        return new RecyclerView.ViewHolder(inflater.inflate(layout, parent, false)) { };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        View v = holder.itemView;
        if (position == 0) {
            listener.onBindHeader(v, items.size());
            return;
        }
        Guardian g = items.get(position - 1);
        ((TextView) v.findViewById(R.id.tvInitial)).setText(g.getInitial());
        ((TextView) v.findViewById(R.id.tvName)).setText(g.name);
        TextView rel = v.findViewById(R.id.tvRelationship);
        rel.setText(g.relationship);
        rel.setVisibility(g.relationship == null || g.relationship.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) v.findViewById(R.id.tvPhone)).setText(g.phoneNumber);

        boolean primary = g.tier == Guardian.TIER_PRIMARY;
        StatusUi.apply(v.findViewById(R.id.tvTier),
                v.getContext().getString(primary ? R.string.guardians_tier_primary_chip : R.string.guardians_tier_secondary_chip),
                primary ? StatusUi.Tone.INFO : StatusUi.Tone.NEUTRAL);
        StatusUi.apply(v.findViewById(R.id.tvInvite),
                v.getContext().getString(R.string.guardians_not_invited), StatusUi.Tone.NEUTRAL);

        View edit = v.findViewById(R.id.btnEdit);
        View remove = v.findViewById(R.id.btnRemove);
        edit.setContentDescription(v.getContext().getString(R.string.guardians_edit_cd, g.name));
        remove.setContentDescription(v.getContext().getString(R.string.guardians_remove_cd, g.name));
        edit.setOnClickListener(x -> listener.onEdit(g));
        remove.setOnClickListener(x -> listener.onRemove(g));
    }
}
