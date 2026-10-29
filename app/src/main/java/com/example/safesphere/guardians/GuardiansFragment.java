package com.example.safesphere.guardians;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safesphere.R;
import com.example.safesphere.data.Guardian;
import com.example.safesphere.database.GuardianRepository;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.EmptyState;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class GuardiansFragment extends Fragment implements GuardianAdapter.Listener {

    private GuardianAdapter adapter;
    private View pbLoading;
    private View fab;
    private int userId;
    private boolean loaded;
    private int tier1Count;
    @Nullable
    private String loadError;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_guardians, container, false);
        userId = new SessionManager(requireContext()).getUserId();
        pbLoading = root.findViewById(R.id.pbLoading);
        fab = root.findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> startActivity(AddGuardianActivity.intent(requireContext(), 0)));

        RecyclerView recycler = root.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new GuardianAdapter(this);
        recycler.setAdapter(adapter);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) load();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) load();
    }

    private void load() {
        if (!loaded) pbLoading.setVisibility(View.VISIBLE);
        Context app = requireContext().getApplicationContext();
        AppExecutors.io(() -> {
            List<Guardian> list;
            String error = null;
            try {
                list = new GuardianRepository(app).getGuardians(userId);
            } catch (RuntimeException e) {
                list = new ArrayList<>();
                error = app.getString(R.string.guardians_error_load);
            }
            List<Guardian> result = list;
            String finalError = error;
            AppExecutors.main(() -> {
                if (!isAdded()) return;
                loaded = true;
                loadError = finalError;
                pbLoading.setVisibility(View.GONE);
                tier1Count = 0;
                for (Guardian g : result) if (g.tier == Guardian.TIER_PRIMARY) tier1Count++;
                adapter.submit(result);
            });
        });
    }

    @Override
    public void onBindHeader(View header, int count) {
        TextView subtitle = header.findViewById(R.id.tvSubtitle);
        subtitle.setText(count == 0
                ? getString(R.string.guardians_subtitle_empty)
                : getResources().getQuantityString(R.plurals.status_guardians_count, count, count, tier1Count));

        View error = header.findViewById(R.id.errorBanner);
        if (loadError != null) {
            error.setVisibility(View.VISIBLE);
            ((TextView) error.findViewById(R.id.tvError)).setText(loadError);
        } else {
            error.setVisibility(View.GONE);
        }

        View empty = header.findViewById(R.id.emptyState);
        boolean showEmpty = loaded && count == 0 && loadError == null;
        empty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        header.findViewById(R.id.tvListLabel).setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            EmptyState.bind(header.findViewById(R.id.emptyInclude), R.drawable.ic_guardians,
                    R.string.guardians_empty_title, R.string.guardians_empty_message,
                    R.string.guardians_add, v -> startActivity(AddGuardianActivity.intent(requireContext(), 0)));
        }
        fab.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onEdit(Guardian g) {
        startActivity(AddGuardianActivity.intent(requireContext(), g.id));
    }

    @Override
    public void onRemove(Guardian g) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.guardians_delete_title)
                .setMessage(getString(R.string.guardians_delete_message, g.name))
                .setPositiveButton(R.string.guardians_delete_confirm, (d, w) -> remove(g))
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void remove(Guardian g) {
        Context app = requireContext().getApplicationContext();
        AppExecutors.io(() -> {
            boolean ok = new GuardianRepository(app).delete(userId, g.id);
            AppExecutors.main(() -> {
                if (!isAdded() || getView() == null) return;
                Snackbar.make(getView(), ok ? getString(R.string.guardians_removed, g.name)
                        : getString(R.string.guardians_error_remove), Snackbar.LENGTH_LONG).show();
                load();
            });
        });
    }
}
