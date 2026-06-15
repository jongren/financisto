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
import java.util.ArrayList;
import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.adapter.SummaryEntityListAdapter;
import ru.orangesoftware.financisto.activity.Report2DChartActivity;
import ru.orangesoftware.financisto.report.ReportType;
import ru.orangesoftware.financisto.utils.MyPreferences;

public class ReportsFragment extends Fragment {

    private ReportType[] reports;
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
        reports = getReportsList();
        ListAdapter adapter = new SummaryEntityListAdapter(getActivity(), reports);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view2, position, id) -> onReportSelected(position));
    }

    private void onReportSelected(int position) {
        if (reports[position].isConventionalBarReport()) {
            android.content.Intent intent = new android.content.Intent(getActivity(), ReportActivity.class);
            intent.putExtra(ReportsListActivity.EXTRA_REPORT_TYPE, reports[position].name());
            startActivity(intent);
        } else {
            android.content.Intent intent = new android.content.Intent(getActivity(), Report2DChartActivity.class);
            intent.putExtra(ru.orangesoftware.financisto.graph.Report2DChart.REPORT_TYPE, reports[position].name());
            startActivity(intent);
        }
    }

    private ReportType[] getReportsList() {
        ArrayList<ReportType> list = new ArrayList<>();
        list.add(ReportType.BY_PERIOD);
        list.add(ReportType.BY_CATEGORY);
        if (MyPreferences.isShowPayee(getActivity())) list.add(ReportType.BY_PAYEE);
        if (MyPreferences.isShowLocation(getActivity())) list.add(ReportType.BY_LOCATION);
        if (MyPreferences.isShowProject(getActivity())) list.add(ReportType.BY_PROJECT);
        list.add(ReportType.BY_ACCOUNT_BY_PERIOD);
        list.add(ReportType.BY_CATEGORY_BY_PERIOD);
        if (MyPreferences.isShowPayee(getActivity())) list.add(ReportType.BY_PAYEE_BY_PERIOD);
        if (MyPreferences.isShowLocation(getActivity())) list.add(ReportType.BY_LOCATION_BY_PERIOD);
        if (MyPreferences.isShowProject(getActivity())) list.add(ReportType.BY_PROJECT_BY_PERIOD);
        return list.toArray(new ReportType[0]);
    }
}
