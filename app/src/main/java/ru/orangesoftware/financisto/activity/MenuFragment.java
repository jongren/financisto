package ru.orangesoftware.financisto.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.adapter.SummaryEntityListAdapter;

public class MenuFragment extends Fragment {

    private ListView listView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.reports_list, container, false);
        listView = v.findViewById(android.R.id.list);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListAdapter adapter = new SummaryEntityListAdapter(getActivity(), MenuListItem.getAvailableItems());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view2, position, id) -> ((MenuListItem) adapter.getItem(position)).call(getActivity()));
    }
}

