package ru.orangesoftware.financisto.activity;

import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import java.util.List;
import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.adapter.BudgetListAdapter;
import ru.orangesoftware.financisto.blotter.BlotterFilter;
import ru.orangesoftware.financisto.datetime.PeriodType;
import ru.orangesoftware.financisto.db.BudgetsTotalCalculator;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.filter.DateTimeCriteria;
import ru.orangesoftware.financisto.filter.WhereFilter;
import ru.orangesoftware.financisto.filter.Criteria;
import ru.orangesoftware.financisto.model.Budget;
import ru.orangesoftware.financisto.model.Total;
import ru.orangesoftware.financisto.utils.RecurUtils;
import ru.orangesoftware.financisto.utils.RecurUtils.Recur;
import ru.orangesoftware.financisto.utils.RecurUtils.RecurInterval;
import ru.orangesoftware.financisto.utils.Utils;

public class BudgetsFragment extends Fragment {

    private DatabaseAdapter db;
    private ArrayList<Budget> budgets;
    private ListAdapter adapter;
    private ListView listView;
    private TextView totalText;
    private ImageButton bFilter;
    private ImageButton bAdd;
    private WhereFilter filter = WhereFilter.empty();
    private static final int FILTER_BUDGET_REQUEST = 1;
    private static final int NEW_BUDGET_REQUEST = 2;
    private static final int EDIT_BUDGET_REQUEST = 3;
    private static final int VIEW_BUDGET_REQUEST = 4;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.budget_list, container, false);
        listView = v.findViewById(android.R.id.list);
        View emptyView = v.findViewById(android.R.id.empty);
        if (emptyView != null) {
            listView.setEmptyView(emptyView);
        }
        totalText = v.findViewById(R.id.total);
        bFilter = v.findViewById(R.id.bFilter);
        bAdd = v.findViewById(R.id.bAdd);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View fragmentView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(fragmentView, savedInstanceState);
        db = new DatabaseAdapter(getActivity());
        db.open();
        if (filter.isEmpty()) {
            filter.put(new DateTimeCriteria(PeriodType.THIS_MONTH));
        }
        budgets = db.getAllBudgets(filter);
        adapter = new BudgetListAdapter(getActivity(), budgets);
        listView.setAdapter(adapter);
        totalText.setOnClickListener(view2 -> startActivity(new android.content.Intent(getActivity(), BudgetListTotalsDetailsActivity.class)));
        bAdd.setOnClickListener(view -> startActivityForResult(new android.content.Intent(getActivity(), BudgetActivity.class), NEW_BUDGET_REQUEST));
        bFilter.setOnClickListener(v1 -> {
            android.content.Intent intent = new android.content.Intent(getActivity(), DateFilterActivity.class);
            filter.toIntent(intent);
            startActivityForResult(intent, FILTER_BUDGET_REQUEST);
        });
        listView.setOnItemClickListener((parent, view, position, id) -> viewBudget(position));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            showBudgetOptions(position, id);
            return true;
        });
        calculateTotals();
    }

    private void calculateTotals() {
        final List<Budget> currentBudgets = budgets;
        new Thread(() -> {
            try {
                BudgetsTotalCalculator c = new BudgetsTotalCalculator(db, currentBudgets);
                // Pass null so updateBudgets sets b.updated synchronously in this background thread
                c.updateBudgets(null);
                final Total total = c.calculateTotalInHomeCurrency();
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (getActivity() == null) return;
                    new Utils(getActivity()).setTotal(totalText, total);
                    if (adapter instanceof BudgetListAdapter) {
                        ((BudgetListAdapter) adapter).setBudgets(currentBudgets);
                        ((BudgetListAdapter) adapter).notifyDataSetChanged();
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("BudgetsFragment", "calculateTotals error", e);
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        if (db != null) db.close();
        super.onDestroyView();
    }

    private void refreshBudgets() {
        if (db == null) {
            db = new DatabaseAdapter(getActivity());
            db.open();
        }
        budgets = db.getAllBudgets(filter);
        if (adapter instanceof BudgetListAdapter) {
            ((BudgetListAdapter) adapter).setBudgets(budgets);
            ((BudgetListAdapter) adapter).notifyDataSetChanged();
        } else {
            adapter = new BudgetListAdapter(getActivity(), budgets);
            listView.setAdapter(adapter);
        }
        calculateTotals();
    }

    public void refresh() {
        refreshBudgets();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NEW_BUDGET_REQUEST || requestCode == EDIT_BUDGET_REQUEST) {
            if (resultCode == android.app.Activity.RESULT_OK) {
                refreshBudgets();
            }
            return;
        }
        if (requestCode == VIEW_BUDGET_REQUEST) {
            refreshBudgets();
            return;
        }
        if (requestCode == FILTER_BUDGET_REQUEST) {
            if (resultCode == android.app.Activity.RESULT_FIRST_USER) {
                filter.clear();
            } else if (resultCode == android.app.Activity.RESULT_OK) {
                String periodType = data.getStringExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_TYPE);
                PeriodType p = PeriodType.valueOf(periodType);
                if (PeriodType.CUSTOM == p) {
                    long periodFrom = data.getLongExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_FROM, 0);
                    long periodTo = data.getLongExtra(DateFilterActivity.EXTRA_FILTER_PERIOD_TO, 0);
                    filter.put(new DateTimeCriteria(periodFrom, periodTo));
                } else {
                    filter.put(new DateTimeCriteria(p));
                }
            }
        }
        refreshBudgets();
    }

    private void viewBudget(int position) {
        Budget b = budgets.get(position);
        Intent intent = new Intent(getActivity(), BudgetBlotterActivity.class);
        Criteria.eq(BlotterFilter.BUDGET_ID, String.valueOf(b.id))
                .toIntent(b.title, intent);
        startActivityForResult(intent, VIEW_BUDGET_REQUEST);
    }

    private void showBudgetOptions(int position, long id) {
        Budget b = budgets.get(position);
        String[] items = {getString(R.string.edit), getString(R.string.delete)};
        new AlertDialog.Builder(getActivity())
                .setTitle(b.title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        editBudget(b, id);
                    } else {
                        deleteBudget(b, id);
                    }
                })
                .show();
    }

    private void editBudget(Budget b, long id) {
        Recur recur = b.getRecur();
        if (recur.interval != RecurInterval.NO_RECUR) {
            Toast.makeText(getActivity(), R.string.edit_recurring_budget, Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(getActivity(), BudgetActivity.class);
        intent.putExtra(BudgetActivity.BUDGET_ID_EXTRA, b.parentBudgetId > 0 ? b.parentBudgetId : id);
        startActivityForResult(intent, EDIT_BUDGET_REQUEST);
    }

    private void deleteBudget(Budget b, long id) {
        if (b.parentBudgetId > 0) {
            new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.delete_budget_recurring_select)
                    .setPositiveButton(R.string.delete_budget_one_entry, (d, w) -> {
                        db.deleteBudgetOneEntry(id);
                        refreshBudgets();
                    })
                    .setNeutralButton(R.string.delete_budget_all_entries, (d, w) -> {
                        db.deleteBudget(b.parentBudgetId);
                        refreshBudgets();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            Recur recur = RecurUtils.createFromExtraString(b.recur);
            new AlertDialog.Builder(getActivity())
                    .setMessage(recur.interval == RecurInterval.NO_RECUR
                            ? R.string.delete_budget_confirm
                            : R.string.delete_budget_recurring_confirm)
                    .setPositiveButton(R.string.yes, (d, w) -> {
                        db.deleteBudget(id);
                        refreshBudgets();
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
        }
    }
}

