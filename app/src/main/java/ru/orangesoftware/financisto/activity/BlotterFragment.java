package ru.orangesoftware.financisto.activity;

import android.database.Cursor;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.view.MenuInflater;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.adapter.BlotterListAdapter;
import ru.orangesoftware.financisto.dialog.TransactionInfoDialog;
import ru.orangesoftware.financisto.blotter.BlotterTotalCalculationTask;
import ru.orangesoftware.financisto.blotter.AccountTotalCalculationTask;
import ru.orangesoftware.financisto.model.Account;
import androidx.annotation.NonNull;
import android.os.AsyncTask;
import ru.orangesoftware.financisto.blotter.BlotterFilter;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.filter.WhereFilter;
import ru.orangesoftware.financisto.filter.Criteria;
import ru.orangesoftware.financisto.view.NodeInflater;
import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.activity.FilterState;
import greendroid.widget.QuickActionGrid;
import ru.orangesoftware.financisto.activity.MyQuickAction;
import androidx.appcompat.app.AlertDialog;
import ru.orangesoftware.financisto.model.Transaction;
import ru.orangesoftware.financisto.model.TransactionStatus;
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity;
import ru.orangesoftware.financisto.activity.AccountWidget;
import ru.orangesoftware.financisto.activity.SelectTemplateActivity;
import ru.orangesoftware.financisto.activity.TransferActivity;
import ru.orangesoftware.financisto.activity.BlotterFilterActivity;
import ru.orangesoftware.financisto.activity.TransactionActivity;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.model.AccountType;

public class BlotterFragment extends Fragment {

    private DatabaseAdapter db;
    private Cursor cursor;
    private ListAdapter adapter;
    private ListView listView;
    private TextView totalText;
    private NodeInflater inflater;
    private ImageButton bFilter;
    private ImageButton bTransfer;
    private ImageButton bTemplate;
    private ImageButton bSearch;
    private ImageButton bMenu;
    private FrameLayout searchLayout;
    private EditText searchText;
    private ImageButton searchTextClearButton;
    private long selectedId = -1;
    private boolean saveFilter;
    private WhereFilter blotterFilter = WhereFilter.empty();
    private boolean isAccountBlotter = false;
    private boolean showAllBlotterButtons = true;
    private QuickActionGrid transactionActionGrid;
    private LoadBlotterTask loadBlotterTask;
    private ru.orangesoftware.financisto.blotter.TotalCalculationTask totalCalculationTask;
    private static final int NEW_TRANSACTION_REQUEST = 1;
    private static final int NEW_TRANSFER_REQUEST = 3;
    private static final int NEW_TRANSACTION_FROM_TEMPLATE_REQUEST = 5;
    private static final int FILTER_REQUEST = 6;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.blotter, container, false);
        listView = v.findViewById(android.R.id.list);
        View emptyView = v.findViewById(android.R.id.empty);
        if (emptyView != null) {
            listView.setEmptyView(emptyView);
        }
        totalText = v.findViewById(R.id.total);
        ImageButton bAdd = v.findViewById(R.id.bAdd);
        bTransfer = v.findViewById(R.id.bTransfer);
        bTemplate = v.findViewById(R.id.bTemplate);
        bSearch = v.findViewById(R.id.bSearch);
        bFilter = v.findViewById(R.id.bFilter);
        bMenu = v.findViewById(R.id.bMenu);
        searchLayout = v.findViewById(R.id.search_text_frame);
        searchText = v.findViewById(R.id.search_text);
        searchTextClearButton = v.findViewById(R.id.search_text_clear);
        bAdd.setOnClickListener(view -> addItem(TransactionActivity.class));
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = new DatabaseAdapter(getActivity());
        db.open();
        Intent intent = getActivity().getIntent();
        if (intent != null) {
            blotterFilter = WhereFilter.fromIntent(intent);
            saveFilter = intent.getBooleanExtra(BlotterActivity.SAVE_FILTER, false);
            isAccountBlotter = intent.getBooleanExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, false);
        }
        if (savedInstanceState != null) {
            blotterFilter = WhereFilter.fromBundle(savedInstanceState);
        }
        if (saveFilter && blotterFilter.isEmpty()) {
            blotterFilter = WhereFilter.fromSharedPreferences(getActivity().getPreferences(0));
        }
        showAllBlotterButtons = !isAccountBlotter && !MyPreferences.isCollapseBlotterButtons(getActivity());

        adapter = new BlotterListAdapter(getActivity(), db, null);
        listView.setAdapter(adapter);
        LayoutInflater layoutInflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater = new NodeInflater(layoutInflater);
        listView.setOnItemClickListener((parent, view2, position, id) -> onItemClick(view2, position, id));
        listView.setOnItemLongClickListener((parent, view2, position, id) -> {
            selectedId = id;
            prepareTransactionActionGrid();
            transactionActionGrid.show(view2);
            return true;
        });
        totalText.setOnClickListener(view2 -> showTotals());

        if (showAllBlotterButtons) {
            bTransfer.setVisibility(View.VISIBLE);
            bTransfer.setOnClickListener(v -> addItem(TransferActivity.class));
            bTemplate.setVisibility(View.VISIBLE);
            bTemplate.setOnClickListener(v -> startActivityForResult(new Intent(getActivity(), SelectTemplateActivity.class), NEW_TRANSACTION_FROM_TEMPLATE_REQUEST));
        } else {
            bTransfer.setVisibility(View.GONE);
            bTemplate.setVisibility(View.GONE);
        }

        bFilter.setOnClickListener(v -> {
            Intent fi = new Intent(getActivity(), BlotterFilterActivity.class);
            blotterFilter.toIntent(fi);
            fi.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, isAccountBlotter && blotterFilter.getAccountId() > 0);
            startActivityForResult(fi, FILTER_REQUEST);
        });

        bSearch.setOnClickListener(method -> {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            searchText.setOnFocusChangeListener((view2, b) -> {
                if (!view2.hasFocus()) {
                    imm.hideSoftInputFromWindow(searchLayout.getWindowToken(), 0);
                }
            });
            searchTextClearButton.setOnClickListener(view2 -> {
                searchText.setText("");
            });
            if (searchLayout.getVisibility() == View.VISIBLE) {
                imm.hideSoftInputFromWindow(searchLayout.getWindowToken(), 0);
                searchLayout.setVisibility(View.GONE);
                return;
            }
            searchLayout.setVisibility(View.VISIBLE);
            searchText.requestFocusFromTouch();
            imm.showSoftInput(searchText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            searchText.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable editable) {
                    String text = editable.toString();
                    blotterFilter.remove(BlotterFilter.NOTE);
                    if (!text.isEmpty()) {
                        blotterFilter.contains(BlotterFilter.NOTE, text);
                        searchTextClearButton.setVisibility(View.VISIBLE);
                    } else {
                        searchTextClearButton.setVisibility(View.GONE);
                    }
                    recreateCursor();
                    applyFilter();
                    saveFilter();
                }
            });
            if (blotterFilter.get(BlotterFilter.NOTE) != null) {
                String searchFilterText = blotterFilter.get(BlotterFilter.NOTE).getStringValue();
                if (!searchFilterText.isEmpty()) {
                    searchFilterText = searchFilterText.substring(1, searchFilterText.length() - 1);
                    searchText.setText(searchFilterText);
                }
            }
        });

        applyFilter();
        setupMenuButton();
        recreateCursor();
    }

    

    @Override
    public void onDestroyView() {
        if (loadBlotterTask != null) loadBlotterTask.cancel(true);
        if (totalCalculationTask != null) {
            totalCalculationTask.stop();
            totalCalculationTask.cancel(true);
        }
        if (cursor != null) cursor.close();
        if (db != null) db.close();
        super.onDestroyView();
    }

    private void showTransactionInfo(long id) {
        TransactionInfoDialog transactionInfoView = new TransactionInfoDialog(getActivity(), db, inflater);
        transactionInfoView.show(getActivity(), id, () -> editTransaction(id, false));
    }

    private void addItem(Class<? extends AbstractTransactionActivity> clazz) {
        Intent intent = new Intent(getActivity(), clazz);
        long accountId = blotterFilter.getAccountId();
        if (accountId != -1) {
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId);
        }
        intent.putExtra(TransactionActivity.TEMPLATE_EXTRA, blotterFilter.getIsTemplate());
        startActivityForResult(intent, clazz == TransferActivity.class ? NEW_TRANSFER_REQUEST : NEW_TRANSACTION_REQUEST);
    }

    private void calculateTotals() {
        if (totalCalculationTask != null) {
            totalCalculationTask.stop();
            totalCalculationTask.cancel(true);
        }
        WhereFilter filter = WhereFilter.copyOf(blotterFilter);
        if (filter.getAccountId() > 0) {
            totalCalculationTask = new AccountTotalCalculationTask(getActivity(), db, filter, totalText);
        } else {
            totalCalculationTask = new BlotterTotalCalculationTask(getActivity(), db, filter, totalText);
        }
        totalCalculationTask.execute();
    }

    private void showTotals() {
        Intent intent = new Intent(getActivity(), BlotterTotalsDetailsActivity.class);
        blotterFilter.toIntent(intent);
        startActivityForResult(intent, -1);
    }

    private void saveFilter() {
        SharedPreferences preferences = getActivity().getPreferences(0);
        blotterFilter.toSharedPreferences(preferences);
    }

    private void applyFilter() {
        String title = blotterFilter.getTitle();
        if (title != null) {
            getActivity().setTitle(getString(R.string.blotter) + " : " + title);
        }
        FilterState.updateFilterColor(getActivity(), blotterFilter, bFilter);
    }

    private void setupMenuButton() {
        if (isAccountBlotter) {
            bMenu.setVisibility(View.VISIBLE);
            bMenu.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(getActivity(), bMenu);
                long accountId = blotterFilter.getAccountId();
                if (accountId != -1) {
                    Account account = db.getAccount(accountId);
                    AccountType type = AccountType.valueOf(account.type);
                    MenuInflater mi = requireActivity().getMenuInflater();
                    if (type.isCreditCard) {
                        mi.inflate(R.menu.ccard_blotter_menu, popupMenu.getMenu());
                    } else {
                        mi.inflate(R.menu.blotter_menu, popupMenu.getMenu());
                    }
                    popupMenu.setOnMenuItemClickListener(item -> {
                        onPopupMenuSelected(item.getItemId());
                        return true;
                    });
                    popupMenu.show();
                }
            });
        } else {
            bMenu.setVisibility(View.GONE);
        }
    }

    public boolean onBackPressed() {
        if (searchLayout != null && searchLayout.getVisibility() == View.VISIBLE) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(searchLayout.getWindowToken(), 0);
            searchLayout.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    private void recreateCursor() {
        if (loadBlotterTask != null) {
            loadBlotterTask.cancel(true);
        }
        loadBlotterTask = new LoadBlotterTask(blotterFilter, isAccountBlotter);
        loadBlotterTask.execute();
    }

    private class LoadBlotterTask extends AsyncTask<Void, Void, Cursor> {
        private final WhereFilter filterCopy;
        private final boolean isAccountBlotterCopy;

        LoadBlotterTask(WhereFilter filter, boolean isAccountBlotter) {
            this.filterCopy = WhereFilter.copyOf(filter);
            this.isAccountBlotterCopy = isAccountBlotter;
        }

        @Override
        protected Cursor doInBackground(Void... params) {
            if (db == null) {
                db = new DatabaseAdapter(getActivity());
                db.open();
            }
            return isAccountBlotterCopy ? db.getBlotterForAccount(filterCopy) : db.getBlotter(filterCopy);
        }

        @Override
        protected void onPostExecute(Cursor newCursor) {
            if (!isAdded() || getActivity() == null) {
                if (newCursor != null) {
                    newCursor.close();
                }
                return;
            }
            if (cursor != null) cursor.close();
            cursor = newCursor;
            if (adapter instanceof BlotterListAdapter) {
                ((BlotterListAdapter) adapter).changeCursor(cursor);
            } else {
                adapter = new BlotterListAdapter(getActivity(), db, cursor);
                listView.setAdapter(adapter);
            }
            calculateTotals();
        }
    }

    public void refresh() {
        recreateCursor();
    }

    public void showAccount(long accountId, String title) {
        isAccountBlotter = true;
        blotterFilter = WhereFilter.empty();
        blotterFilter.put(Criteria.eq(BlotterFilter.FROM_ACCOUNT_ID, String.valueOf(accountId)));
        recreateCursor();
        applyFilter();
        setupMenuButton();
    }

    private void createTransactionFromTemplate(Intent data) {
        long templateId = data.getLongExtra(SelectTemplateActivity.TEMPATE_ID, -1);
        int multiplier = data.getIntExtra(SelectTemplateActivity.MULTIPLIER, 1);
        boolean edit = data.getBooleanExtra(SelectTemplateActivity.EDIT_AFTER_CREATION, false);
        if (templateId > 0) {
            long id = duplicateTransaction(templateId, multiplier);
            Transaction t = db.getTransaction(id);
            if (t.fromAmount == 0 || edit) {
                editTransaction(id, true);
            }
        }
    }

    private void onItemClick(View v, int position, long id) {
        if (MyPreferences.isQuickMenuEnabledForTransaction(getActivity())) {
            selectedId = id;
            prepareTransactionActionGrid();
            transactionActionGrid.show(v);
        } else {
            showTransactionInfo(id);
        }
    }

    private void prepareTransactionActionGrid() {
        transactionActionGrid = new QuickActionGrid(getActivity());
        transactionActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_info, R.string.info));
        transactionActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_edit, R.string.edit));
        transactionActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_trash, R.string.delete));
        transactionActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_copy, R.string.duplicate));
        transactionActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_tick, R.string.clear));
        transactionActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_double_tick, R.string.reconcile));
        transactionActionGrid.setOnQuickActionClickListener((widget, pos) -> {
            switch (pos) {
                case 0: showTransactionInfo(selectedId); break;
                case 1: editTransaction(selectedId, false); break;
                case 2: confirmDeleteTransaction(selectedId); break;
                case 3: duplicateTransaction(selectedId, 1); break;
                case 4: clearTransaction(selectedId); break;
                case 5: reconcileTransaction(selectedId); break;
            }
        });
    }

    private void afterDeletingTransaction(long id) {
        recreateCursor();
        AccountWidget.updateWidgets(getActivity());
    }

    private long duplicateTransaction(long id, int multiplier) {
        long newId;
        if (multiplier > 1) {
            newId = db.duplicateTransactionWithMultiplier(id, multiplier);
        } else {
            newId = db.duplicateTransaction(id);
        }
        String toastText = multiplier > 1 ? getString(R.string.duplicate_success_with_multiplier, multiplier) : getString(R.string.duplicate_success);
        android.widget.Toast.makeText(getActivity(), toastText, android.widget.Toast.LENGTH_LONG).show();
        recreateCursor();
        AccountWidget.updateWidgets(getActivity());
        return newId;
    }

    private void editTransaction(long id, boolean newFromTemplate) {
        Transaction t = db.getTransaction(id);
        Class<? extends android.app.Activity> clazz = t.isTransfer() ? TransferActivity.class : TransactionActivity.class;
        Intent intent = new Intent(getActivity(), clazz);
        intent.putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, t.id);
        intent.putExtra(AbstractTransactionActivity.DUPLICATE_EXTRA, false);
        intent.putExtra(AbstractTransactionActivity.NEW_FROM_TEMPLATE_EXTRA, newFromTemplate);
        startActivityForResult(intent, t.isTransfer() ? NEW_TRANSFER_REQUEST : NEW_TRANSACTION_REQUEST);
    }

    private void confirmDeleteTransaction(long id) {
        Transaction originalTransaction = db.getTransaction(id);
        Transaction targetTransaction = originalTransaction.isSplitChild() ? db.getTransaction(originalTransaction.parentId) : originalTransaction;
        int titleId = targetTransaction.isTemplate() ? R.string.delete_template_confirm
                : (originalTransaction.isSplitChild() ? R.string.delete_transaction_parent_confirm : R.string.delete_transaction_confirm);
        new AlertDialog.Builder(getActivity())
                .setMessage(titleId)
                .setPositiveButton(R.string.yes, (arg0, arg1) -> {
                    long transactionIdToDelete = targetTransaction.id;
                    db.deleteTransaction(transactionIdToDelete);
                    afterDeletingTransaction(transactionIdToDelete);
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void clearTransaction(long id) {
        db.updateTransactionStatus(id, TransactionStatus.CL);
        recreateCursor();
    }

    private void reconcileTransaction(long id) {
        db.updateTransactionStatus(id, TransactionStatus.RC);
        recreateCursor();
    }

    private void onPopupMenuSelected(int id) {
        long accountId = blotterFilter.getAccountId();
        Intent intent = new Intent(getActivity(), MonthlyViewActivity.class);
        intent.putExtra(MonthlyViewActivity.ACCOUNT_EXTRA, accountId);
        switch (id) {
            case R.id.opt_menu_month:
                intent.putExtra(MonthlyViewActivity.BILL_PREVIEW_EXTRA, false);
                startActivityForResult(intent, -1);
                break;
            case R.id.opt_menu_bill:
                if (accountId != -1) {
                    Account account = db.getAccount(accountId);
                    AccountType type = AccountType.valueOf(account.type);
                    if (type.isCreditCard && account.paymentDay > 0 && account.closingDay > 0) {
                        intent.putExtra(MonthlyViewActivity.BILL_PREVIEW_EXTRA, true);
                        startActivityForResult(intent, -1);
                    } else {
                        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(getActivity());
                        dlgAlert.setMessage(R.string.statement_error);
                        dlgAlert.setTitle(R.string.ccard_statement);
                        dlgAlert.setPositiveButton(R.string.ok, null);
                        dlgAlert.setCancelable(true);
                        dlgAlert.create().show();
                    }
                }
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILTER_REQUEST) {
            if (resultCode == android.app.Activity.RESULT_FIRST_USER) {
                blotterFilter.clear();
            } else if (resultCode == android.app.Activity.RESULT_OK) {
                blotterFilter = WhereFilter.fromIntent(data);
            }
            if (saveFilter) {
                saveFilter();
            }
            applyFilter();
            recreateCursor();
            return;
        } else if (resultCode == android.app.Activity.RESULT_OK && requestCode == NEW_TRANSACTION_FROM_TEMPLATE_REQUEST) {
            createTransactionFromTemplate(data);
        }
        if (resultCode == android.app.Activity.RESULT_OK || resultCode == android.app.Activity.RESULT_FIRST_USER) {
            recreateCursor();
        }
    }
}

