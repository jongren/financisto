package ru.orangesoftware.financisto.activity;

import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import greendroid.widget.QuickActionGrid;
import android.widget.ListAdapter;
import androidx.annotation.NonNull;
import android.os.AsyncTask;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.adapter.AccountListAdapter2;
import ru.orangesoftware.financisto.bus.GreenRobotBus_;
import ru.orangesoftware.financisto.bus.SwitchToMenuTabEvent;
import ru.orangesoftware.financisto.bus.SwitchToBlotterTabEvent;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.model.Account;
import ru.orangesoftware.financisto.filter.WhereFilter;
import ru.orangesoftware.financisto.utils.MyPreferences;
import ru.orangesoftware.financisto.view.NodeInflater;
import ru.orangesoftware.financisto.dialog.AccountInfoDialog;
import ru.orangesoftware.financisto.activity.MyQuickAction;
import android.content.Intent;
import androidx.appcompat.app.AlertDialog;

public class AccountsFragment extends Fragment {

    private DatabaseAdapter db;
    private Cursor cursor;
    private ListAdapter adapter;
    private ListView listView;
    private TextView totalText;
    private QuickActionGrid accountActionGrid;
    private AccountListActivity.AccountTotalsCalculationTask totalCalculationTask;
    private long selectedId = -1;
    private LoadAccountsTask loadAccountsTask;
    private static final int NEW_ACCOUNT_REQUEST = 1;
    private static final int EDIT_ACCOUNT_REQUEST = 2;
    private static final int VIEW_ACCOUNT_REQUEST = 3;
    private static final int PURGE_ACCOUNT_REQUEST = 4;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.account_list, container, false);
        listView = v.findViewById(android.R.id.list);
        View emptyView = v.findViewById(android.R.id.empty);
        if (emptyView != null) {
            listView.setEmptyView(emptyView);
        }
        totalText = v.findViewById(R.id.total);
        ImageButton bAdd = v.findViewById(R.id.bAdd);
        bAdd.setOnClickListener(view -> startActivityForResult(new android.content.Intent(getActivity(), AccountActivity.class), NEW_ACCOUNT_REQUEST));
        v.findViewById(R.id.integrity_error).setOnClickListener(view -> view.setVisibility(View.GONE));
        totalText.setOnClickListener(view -> startActivityForResult(new android.content.Intent(getActivity(), AccountListTotalsDetailsActivity.class), -1));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (MyPreferences.isQuickMenuEnabledForAccount(getActivity())) {
                selectedId = id;
                prepareAccountActionGrid();
                accountActionGrid.show(view);
            } else {
                showAccountTransactions(id);
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            selectedId = id;
            prepareAccountActionGrid();
            accountActionGrid.show(view);
            return true;
        });
        ImageButton bMenu = v.findViewById(R.id.bMenu);
        if (MyPreferences.isShowMenuButtonOnAccountsScreen(getActivity())) {
            bMenu.setOnClickListener(v1 -> {
                PopupMenu popupMenu = new PopupMenu(getActivity(), bMenu);
                android.view.MenuInflater menuInflater = requireActivity().getMenuInflater();
                menuInflater.inflate(R.menu.account_list_menu, popupMenu.getMenu());
                if (!ru.orangesoftware.financisto.BuildConfig.DEBUG) {
                    popupMenu.getMenu().removeItem(R.id.google_sheets_sync);
                }
                popupMenu.setOnMenuItemClickListener(item -> {
                    handlePopupMenu(item.getItemId());
                    return true;
                });
                popupMenu.show();
            });
        } else {
            bMenu.setVisibility(View.GONE);
        }
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = new DatabaseAdapter(getActivity());
        db.open();
        refreshAccounts();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VIEW_ACCOUNT_REQUEST || requestCode == PURGE_ACCOUNT_REQUEST) {
            refreshAccounts();
            return;
        }
        if (resultCode == android.app.Activity.RESULT_OK && (requestCode == NEW_ACCOUNT_REQUEST || requestCode == EDIT_ACCOUNT_REQUEST)) {
            refreshAccounts();
        }
    }

    private void refreshAccounts() {
        if (loadAccountsTask != null) {
            loadAccountsTask.cancel(true);
        }
        loadAccountsTask = new LoadAccountsTask();
        loadAccountsTask.execute();
    }

    private class LoadAccountsTask extends AsyncTask<Void, Void, Cursor> {
        @Override
        protected Cursor doInBackground(Void... params) {
            if (db == null) {
                db = new DatabaseAdapter(getActivity());
                db.open();
            }
            if (MyPreferences.isHideClosedAccounts(getActivity())) {
                return db.getAllActiveAccounts();
            } else {
                return db.getAllAccounts();
            }
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
            if (adapter instanceof android.widget.ResourceCursorAdapter) {
                ((android.widget.ResourceCursorAdapter) adapter).changeCursor(cursor);
            } else {
                adapter = new AccountListAdapter2(getActivity(), cursor);
                listView.setAdapter(adapter);
            }
            if (totalCalculationTask != null) {
                totalCalculationTask.stop();
                totalCalculationTask.cancel(true);
            }
            totalCalculationTask = new AccountListActivity.AccountTotalsCalculationTask(getActivity(), db, totalText);
            totalCalculationTask.execute();
        }
    }

    public void refresh() {
        refreshAccounts();
    }

    @Override
    public void onDestroyView() {
        if (loadAccountsTask != null) loadAccountsTask.cancel(true);
        if (totalCalculationTask != null) {
            totalCalculationTask.stop();
            totalCalculationTask.cancel(true);
        }
        if (cursor != null) cursor.close();
        if (db != null) db.close();
        super.onDestroyView();
    }

    private void showAccountTransactions(long id) {
        Account account = db.getAccount(id);
        if (account != null) {
            Intent intent = new Intent(getActivity(), BlotterActivity.class);
            ru.orangesoftware.financisto.filter.Criteria.eq(ru.orangesoftware.financisto.blotter.BlotterFilter.FROM_ACCOUNT_ID, String.valueOf(id))
                    .toIntent(account.title, intent);
            intent.putExtra(ru.orangesoftware.financisto.activity.BlotterFilterActivity.IS_ACCOUNT_FILTER, true);
            startActivityForResult(intent, VIEW_ACCOUNT_REQUEST);
        }
    }

    private void prepareAccountActionGrid() {
        Account a = db.getAccount(selectedId);
        accountActionGrid = new QuickActionGrid(getActivity());
        accountActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_info, R.string.info));
        accountActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_list, R.string.blotter));
        accountActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_edit, R.string.edit));
        accountActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_add, R.string.transaction));
        accountActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_transfer, R.string.transfer));
        accountActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_tick, R.string.balance));
        accountActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_flash, R.string.delete_old_transactions));
        if (a.isActive) {
            accountActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_lock_closed, R.string.close_account));
        } else {
            accountActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_lock_open, R.string.reopen_account));
        }
        accountActionGrid.addQuickAction(new MyQuickAction(getActivity(), R.drawable.ic_action_trash, R.string.delete_account));
        accountActionGrid.setOnQuickActionClickListener((widget, position) -> {
            switch (position) {
                case 0:
                    showAccountInfo(selectedId);
                    break;
                case 1:
                    showAccountTransactions(selectedId);
                    break;
                case 2:
                    editAccount(selectedId);
                    break;
                case 3:
                    addTransaction(selectedId, TransactionActivity.class);
                    break;
                case 4:
                    addTransaction(selectedId, TransferActivity.class);
                    break;
                case 5:
                    updateAccountBalance(selectedId);
                    break;
                case 6:
                    purgeAccount();
                    break;
                case 7:
                    closeOrOpenAccount();
                    break;
                case 8:
                    deleteAccount();
                    break;
            }
        });
    }

    private void showAccountInfo(long id) {
        LayoutInflater layoutInflater = (LayoutInflater) getActivity().getSystemService(android.content.Context.LAYOUT_INFLATER_SERVICE);
        NodeInflater inflater = new NodeInflater(layoutInflater);
        AccountInfoDialog.show(getActivity(), id, db, inflater, () -> editAccount(id));
    }

    private void editAccount(long id) {
        Intent intent = new Intent(getActivity(), AccountActivity.class);
        intent.putExtra(AccountActivity.ACCOUNT_ID_EXTRA, id);
        startActivityForResult(intent, EDIT_ACCOUNT_REQUEST);
    }

    private void addTransaction(long accountId, Class<? extends AbstractTransactionActivity> clazz) {
        Intent intent = new Intent(getActivity(), clazz);
        intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId);
        startActivityForResult(intent, VIEW_ACCOUNT_REQUEST);
    }

    private boolean updateAccountBalance(long id) {
        Account a = db.getAccount(id);
        if (a != null) {
            Intent intent = new Intent(getActivity(), TransactionActivity.class);
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, a.id);
            intent.putExtra(TransactionActivity.CURRENT_BALANCE_EXTRA, a.totalAmount);
            startActivityForResult(intent, 0);
            return true;
        }
        return false;
    }

    private void purgeAccount() {
        Intent intent = new Intent(getActivity(), PurgeAccountActivity.class);
        intent.putExtra(PurgeAccountActivity.ACCOUNT_ID, selectedId);
        startActivityForResult(intent, PURGE_ACCOUNT_REQUEST);
    }

    private void closeOrOpenAccount() {
        Account a = db.getAccount(selectedId);
        if (a.isActive) {
            new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.close_account_confirm)
                    .setPositiveButton(R.string.yes, (arg0, arg1) -> flipAccountActive(a))
                    .setNegativeButton(R.string.no, null)
                    .show();
        } else {
            flipAccountActive(a);
        }
    }

    private void flipAccountActive(Account a) {
        a.isActive = !a.isActive;
        db.saveAccount(a);
        refreshAccounts();
    }

    private void deleteAccount() {
        new AlertDialog.Builder(getActivity())
                .setMessage(R.string.delete_account_confirm)
                .setPositiveButton(R.string.yes, (arg0, arg1) -> {
                    db.deleteAccount(selectedId);
                    refreshAccounts();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void handlePopupMenu(int id) {
        switch (id) {
            case R.id.backup:
                MenuListItem.MENU_BACKUP.call(getActivity());
                break;
            case R.id.google_sheets_sync:
                MenuListItem.MENU_GOOGLE_SHEETS_SYNC.call(getActivity());
                break;
            case R.id.go_to_menu:
                GreenRobotBus_.getInstance_(getActivity()).post(new SwitchToMenuTabEvent());
                break;
        }
    }
}
