package com.example.safesphere.history;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safesphere.R;
import com.example.safesphere.data.SafetyEvent;
import com.example.safesphere.database.EventRepository;
import com.example.safesphere.home.MainActivity;
import com.example.safesphere.session.SessionManager;
import com.example.safesphere.sos.EmergencyStatusActivity;
import com.example.safesphere.utils.AppExecutors;
import com.example.safesphere.utils.EmptyState;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class HistoryFragment extends Fragment {

    private static final int PAGE_LIMIT = 200;

    private HistoryAdapter adapter;
    private View pbLoading;
    private View emptyScroll;
    private View root;
    private int userId;
    private String filter = EventRepository.FILTER_ALL;
    private boolean loadedOnce;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_history, container, false);
        userId = new SessionManager(requireContext()).getUserId();
        pbLoading = root.findViewById(R.id.pbLoading);
        emptyScroll = root.findViewById(R.id.emptyScroll);

        RecyclerView recycler = root.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter(this::open);
        recycler.setAdapter(adapter);

        ChipGroup chips = root.findViewById(R.id.filterChips);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            filter = id == R.id.chipSos ? SafetyEvent.TYPE_SOS
                    : id == R.id.chipJourney ? SafetyEvent.TYPE_JOURNEY
                    : id == R.id.chipOther ? EventRepository.FILTER_OTHER
                    : EventRepository.FILTER_ALL;
            load();
        });
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
        if (!loadedOnce) pbLoading.setVisibility(View.VISIBLE);
        Context app = requireContext().getApplicationContext();
        String currentFilter = filter;
        AppExecutors.io(() -> {
            List<SafetyEvent> events = null;
            try {
                events = new EventRepository(app).getEvents(userId, currentFilter, PAGE_LIMIT);
            } catch (RuntimeException ignored) {
                // Shown as an error state below.
            }
            List<SafetyEvent> result = events;
            AppExecutors.main(() -> {
                if (!isAdded() || root == null || !currentFilter.equals(filter)) return;
                loadedOnce = true;
                pbLoading.setVisibility(View.GONE);
                if (result == null) {
                    adapter.submit(java.util.Collections.emptyList());
                    emptyScroll.setVisibility(View.VISIBLE);
                    EmptyState.bind(root.findViewById(R.id.emptyInclude), R.drawable.ic_warning,
                            R.string.history_error_title, R.string.history_error_message,
                            R.string.common_retry, v -> load());
                    return;
                }
                adapter.submit(result);
                emptyScroll.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                if (result.isEmpty()) {
                    boolean all = EventRepository.FILTER_ALL.equals(currentFilter);
                    EmptyState.bind(root.findViewById(R.id.emptyInclude), R.drawable.ic_history,
                            all ? R.string.history_empty_title : R.string.history_empty_filtered_title,
                            R.string.history_empty_message, 0, null);
                }
            });
        });
    }

    private void open(SafetyEvent e) {
        if (SafetyEvent.TYPE_SOS.equals(e.type) && e.isOpen()) {
            startActivity(EmergencyStatusActivity.intent(requireContext(), e.id));
        } else if (SafetyEvent.TYPE_JOURNEY.equals(e.type) && e.isOpen()) {
            MainActivity main = MainActivity.from(this);
            if (main != null) main.selectTab(R.id.nav_journey);
        } else {
            startActivity(HistoryDetailsActivity.intent(requireContext(), e.id));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        root = null;
    }
}
