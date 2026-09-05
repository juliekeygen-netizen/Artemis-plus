package com.limelight.quickmenu;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limelight.R;
import com.limelight.ui.ArtemisEditorUi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared editor for the persisted in-stream Quick Menu tree. */
public final class QuickMenuEditorDialog {
    private QuickMenuEditorDialog() {}

    public static void show(Context context) {
        new EditorSession(context).show();
    }

    private static final class EditorSession {
        private final Context app;
        private final Context ui;
        private QuickMenuConfig config;
        private final List<QuickMenuConfig.Page> pageStack = new ArrayList<>();
        private TextView titleView;
        private TextView backButton;
        private TextView emptyState;
        private RecyclerView list;
        private PageAdapter adapter;
        private AlertDialog dialog;

        EditorSession(Context context) {
            app = context;
            ui = ArtemisEditorUi.context(context);
            config = QuickMenuConfig.load(context);
            pageStack.add(config.root);
        }

        void show() {
            int pad = ArtemisEditorUi.dp(ui, 12);
            LinearLayout root = new LinearLayout(ui);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(pad, ArtemisEditorUi.dp(ui, 6), pad, ArtemisEditorUi.dp(ui, 8));
            root.setBackgroundColor(ArtemisEditorUi.SURFACE);
            ScrollView editorScroll = new ScrollView(ui);
            editorScroll.setFillViewport(false);
            editorScroll.setClipToPadding(false);
            editorScroll.addView(root, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

            LinearLayout nav = new LinearLayout(ui);
            nav.setGravity(Gravity.CENTER_VERTICAL);
            backButton = compactButton(ui.getString(R.string.artemis_quick_menu_back), false);
            nav.addView(backButton, new LinearLayout.LayoutParams(
                    ArtemisEditorUi.dp(ui, 78), ArtemisEditorUi.dp(ui, 42)));
            titleView = ArtemisEditorUi.label(ui,
                    ui.getString(R.string.artemis_quick_menu_root_title), 15.5f,
                    ArtemisEditorUi.TEXT_PRIMARY);
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            titleView.setMaxLines(1);
            nav.addView(titleView, new LinearLayout.LayoutParams(0,
                    ArtemisEditorUi.dp(ui, 42), 1f));
            root.addView(nav, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 42)));

            emptyState = ArtemisEditorUi.label(ui,
                    ui.getString(R.string.artemis_quick_menu_empty),
                    13.5f, ArtemisEditorUi.TEXT_SECONDARY);
            emptyState.setGravity(Gravity.CENTER);
            emptyState.setPadding(pad, pad, pad, pad);

            FrameLayout listFrame = new FrameLayout(ui);
            list = new RecyclerView(ui);
            list.setLayoutManager(new LinearLayoutManager(ui));
            list.setClipToPadding(false);
            list.setPadding(0, ArtemisEditorUi.dp(ui, 2), 0, ArtemisEditorUi.dp(ui, 4));
            listFrame.addView(list, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            listFrame.addView(emptyState, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            int listHeight = Math.min(ArtemisEditorUi.dp(ui, 360),
                    Math.max(ArtemisEditorUi.dp(ui, 180),
                            contextHeight(app) / 2));
            root.addView(listFrame, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, listHeight));

            adapter = new PageAdapter();
            list.setAdapter(adapter);
            new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                      RecyclerView.ViewHolder target) {
                    int from = viewHolder.getBindingAdapterPosition();
                    int to = target.getBindingAdapterPosition();
                    if (QuickMenuConfig.move(currentPage(), from, to)) {
                        adapter.notifyItemMoved(from, to);
                        save();
                        return true;
                    }
                    return false;
                }

                @Override public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {}
                @Override public boolean isLongPressDragEnabled() { return true; }
            }).attachToRecyclerView(list);

            LinearLayout firstActions = new LinearLayout(ui);
            firstActions.setGravity(Gravity.CENTER);
            TextView addAction = compactButton(ui.getString(R.string.artemis_quick_menu_add_action), true);
            TextView addPage = compactButton(ui.getString(R.string.artemis_quick_menu_add_subpage), true);
            firstActions.addView(addAction, weightedButtonParams());
            firstActions.addView(addPage, weightedButtonParams());
            root.addView(firstActions, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 48)));

            LinearLayout secondActions = new LinearLayout(ui);
            secondActions.setGravity(Gravity.CENTER);
            TextView rename = compactButton(ui.getString(R.string.artemis_quick_menu_rename_page), false);
            TextView reset = compactButton(ui.getString(R.string.artemis_quick_menu_reset_defaults), false);
            secondActions.addView(rename, weightedButtonParams());
            secondActions.addView(reset, weightedButtonParams());
            root.addView(secondActions, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 46)));

            addAction.setOnClickListener(v -> showActionPicker());
            addPage.setOnClickListener(v -> {
                if (QuickMenuConfig.countNodes(config.root) >= QuickMenuConfig.MAX_TOTAL_NODES) {
                    Toast.makeText(app, R.string.artemis_quick_menu_item_limit, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (pageStack.size() - 1 >= QuickMenuConfig.MAX_PAGE_DEPTH) {
                    Toast.makeText(app, R.string.artemis_quick_menu_depth_limit, Toast.LENGTH_SHORT).show();
                    return;
                }
                promptForName(ui.getString(R.string.artemis_quick_menu_add_subpage_title),
                        ui.getString(R.string.artemis_quick_menu_page_name_hint), "", name -> {
                    QuickMenuConfig.Page child = QuickMenuConfig.addPage(currentPage(), name);
                    if (child != null) {
                        save();
                        refresh();
                        openPage(child);
                    }
                });
            });
            rename.setOnClickListener(v -> promptForName(
                    ui.getString(R.string.artemis_quick_menu_rename_page_title),
                    ui.getString(R.string.artemis_quick_menu_page_name_hint), currentPage().title, name -> {
                        QuickMenuConfig.rename(currentPage(), name);
                        save();
                        refresh();
                    }));
            reset.setOnClickListener(v -> {
                AlertDialog confirmation = ArtemisEditorUi.builder(ui,
                                ui.getString(R.string.artemis_quick_menu_reset_title))
                    .setMessage(R.string.artemis_quick_menu_reset_message)
                    .setPositiveButton(R.string.artemis_reset, (d, which) -> {
                        QuickMenuConfig.reset(app);
                        config = QuickMenuConfig.createDefault();
                        pageStack.clear();
                        pageStack.add(config.root);
                        save();
                        refresh();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
                confirmation.setOnShowListener(ignored ->
                        ArtemisEditorUi.styleDialog(confirmation, app, 440));
                confirmation.show();
            });
            backButton.setOnClickListener(v -> {
                if (pageStack.size() > 1) {
                    pageStack.remove(pageStack.size() - 1);
                    refresh();
                }
            });

            dialog = ArtemisEditorUi.builder(ui,
                            ui.getString(R.string.artemis_quick_menu_title))
                    .setView(editorScroll)
                    .setNegativeButton(R.string.artemis_close, null)
                    .create();
            dialog.setOnShowListener(ignored -> {
                ArtemisEditorUi.styleDialog(dialog, app, 560, 620, true);
                refresh();
            });
            dialog.show();
        }

        private QuickMenuConfig.Page currentPage() {
            return pageStack.get(pageStack.size() - 1);
        }

        private void openPage(QuickMenuConfig.Page page) {
            if (page == null || pageStack.size() - 1 >= QuickMenuConfig.MAX_PAGE_DEPTH) return;
            pageStack.add(page);
            refresh();
        }

        private void refresh() {
            if (adapter == null) return;
            titleView.setText(buildBreadcrumb());
            backButton.setVisibility(pageStack.size() > 1 ? View.VISIBLE : View.INVISIBLE);
            emptyState.setVisibility(currentPage().items.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.notifyDataSetChanged();
        }

        private String buildBreadcrumb() {
            if (pageStack.size() == 1) return currentPage().title;
            String parent = pageStack.get(pageStack.size() - 2).title;
            return parent + "  ›  " + currentPage().title;
        }

        private void save() {
            QuickMenuConfig.save(app, config);
        }

        private LinearLayout.LayoutParams weightedButtonParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ArtemisEditorUi.dp(ui, 40), 1f);
            params.setMargins(ArtemisEditorUi.dp(ui, 3), ArtemisEditorUi.dp(ui, 3),
                    ArtemisEditorUi.dp(ui, 3), ArtemisEditorUi.dp(ui, 3));
            return params;
        }

        private TextView compactButton(String text, boolean accent) {
            TextView button = new TextView(ui);
            button.setText(text);
            button.setTextColor(accent ? ArtemisEditorUi.ACCENT : ArtemisEditorUi.TEXT_SECONDARY);
            button.setTextSize(13f);
            button.setGravity(Gravity.CENTER);
            button.setBackground(ArtemisEditorUi.rounded(ui,
                    ArtemisEditorUi.SURFACE_RAISED, 8, 1, ArtemisEditorUi.BORDER));
            button.setPadding(ArtemisEditorUi.dp(ui, 8), 0, ArtemisEditorUi.dp(ui, 8), 0);
            return button;
        }

        private void showActionPicker() {
            LinearLayout root = new LinearLayout(ui);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = ArtemisEditorUi.dp(ui, 12);
            root.setPadding(pad, ArtemisEditorUi.dp(ui, 8), pad, ArtemisEditorUi.dp(ui, 8));
            root.setBackgroundColor(ArtemisEditorUi.SURFACE);

            EditText search = new EditText(ui);
            search.setSingleLine(true);
            search.setHint(R.string.artemis_quick_menu_search_hint);
            search.setTextColor(Color.WHITE);
            search.setHintTextColor(0xFF8E8E93);
            search.setBackground(ArtemisEditorUi.rounded(ui,
                    ArtemisEditorUi.SURFACE_RAISED, 8, 1, ArtemisEditorUi.BORDER));
            search.setPadding(ArtemisEditorUi.dp(ui, 12), 0, ArtemisEditorUi.dp(ui, 12), 0);
            root.addView(search, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 44)));

            List<Integer> categoryResIds = new ArrayList<>();
            categoryResIds.add(0); // Sentinel for "All categories"; never persisted or compared to action IDs.
            categoryResIds.addAll(StreamActionRegistry.getCategoryResIds());
            List<String> categories = new ArrayList<>();
            categories.add(ui.getString(R.string.artemis_quick_menu_all_categories));
            for (int categoryResId : StreamActionRegistry.getCategoryResIds()) {
                categories.add(ui.getString(categoryResId));
            }
            Spinner category = new Spinner(ui);
            ArrayAdapter<String> categoryAdapter = new ArrayAdapter<String>(
                    ui, android.R.layout.simple_spinner_dropdown_item, categories) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    if (view instanceof TextView) ((TextView) view).setTextColor(ArtemisEditorUi.TEXT_PRIMARY);
                    return view;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    if (view instanceof TextView) {
                        ((TextView) view).setTextColor(ArtemisEditorUi.TEXT_PRIMARY);
                        view.setBackgroundColor(ArtemisEditorUi.SURFACE_RAISED);
                    }
                    return view;
                }
            };
            category.setAdapter(categoryAdapter);
            root.addView(category, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 44)));

            LinearLayout rows = new LinearLayout(ui);
            rows.setOrientation(LinearLayout.VERTICAL);
            ScrollView scroll = new ScrollView(ui);
            scroll.addView(rows);
            root.addView(scroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 330)));

            AlertDialog picker = ArtemisEditorUi.builder(ui,
                            ui.getString(R.string.artemis_quick_menu_action_picker_title))
                    .setView(root)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();

            Runnable rebuild = () -> {
                String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
                int selectedPosition = category.getSelectedItemPosition();
                int selectedCategoryResId = selectedPosition >= 0 && selectedPosition < categoryResIds.size()
                        ? categoryResIds.get(selectedPosition) : 0;
                rows.removeAllViews();
                for (StreamActionRegistry.ActionDefinition action : StreamActionRegistry.getAll()) {
                    if (selectedCategoryResId != 0 && action.categoryResId != selectedCategoryResId) continue;
                    String label = ui.getString(action.labelResId);
                    String categoryLabel = ui.getString(action.categoryResId);
                    String description = ui.getString(action.descriptionResId);
                    String haystack = (label + " " + categoryLabel + " " + description)
                            .toLowerCase(Locale.ROOT);
                    if (!query.isEmpty() && !haystack.contains(query)) continue;
                    rows.addView(actionPickerRow(action, picker), new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 64)));
                }
                if (rows.getChildCount() == 0) {
                    TextView none = ArtemisEditorUi.label(ui,
                            ui.getString(R.string.artemis_quick_menu_no_matches), 13.5f,
                            ArtemisEditorUi.TEXT_SECONDARY);
                    none.setGravity(Gravity.CENTER);
                    rows.addView(none, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 64)));
                }
            };
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { rebuild.run(); }
                @Override public void afterTextChanged(Editable s) {}
            });
            category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { rebuild.run(); }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
            picker.setOnShowListener(ignored -> {
                ArtemisEditorUi.styleDialog(picker, app, 500, 560, false);
                rebuild.run();
            });
            picker.show();
        }

        private View actionPickerRow(StreamActionRegistry.ActionDefinition action, AlertDialog picker) {
            LinearLayout row = new LinearLayout(ui);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ArtemisEditorUi.dp(ui, 14), ArtemisEditorUi.dp(ui, 7),
                    ArtemisEditorUi.dp(ui, 12), ArtemisEditorUi.dp(ui, 7));
            row.setBackground(ArtemisEditorUi.rounded(ui,
                    ArtemisEditorUi.SURFACE_RAISED, 8, 0, 0));
            TextView label = ArtemisEditorUi.label(ui,
                    ui.getString(action.labelResId), 14.5f, ArtemisEditorUi.TEXT_PRIMARY);
            TextView detail = ArtemisEditorUi.label(ui,
                    ui.getString(action.categoryResId) + "  ·  " + ui.getString(action.descriptionResId),
                    11.5f, ArtemisEditorUi.TEXT_SECONDARY);
            detail.setMaxLines(1);
            row.addView(label);
            row.addView(detail);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 60));
            params.setMargins(0, ArtemisEditorUi.dp(ui, 2), 0, ArtemisEditorUi.dp(ui, 2));
            row.setLayoutParams(params);
            row.setOnClickListener(v -> {
                if (QuickMenuConfig.countNodes(config.root) >= QuickMenuConfig.MAX_TOTAL_NODES) {
                    Toast.makeText(app, R.string.artemis_quick_menu_item_limit, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (QuickMenuConfig.addAction(currentPage(), action.id)) {
                    save();
                    refresh();
                    picker.dismiss();
                }
            });
            return row;
        }

        private void promptForName(String title, String hint, String initial, NameCallback callback) {
            EditText input = new EditText(ui);
            input.setSingleLine(true);
            input.setHint(hint);
            input.setText(initial);
            input.setTextColor(Color.WHITE);
            input.setHintTextColor(0xFF8E8E93);
            input.setPadding(ArtemisEditorUi.dp(ui, 14), input.getPaddingTop(),
                    ArtemisEditorUi.dp(ui, 14), input.getPaddingBottom());
            if (!initial.isEmpty()) input.setSelection(initial.length());
            AlertDialog prompt = ArtemisEditorUi.builder(ui, title)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            prompt.setOnShowListener(ignored -> {
                ArtemisEditorUi.styleDialog(prompt, app, 420);
                prompt.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        input.setError(ui.getString(R.string.artemis_quick_menu_page_name_required));
                        return;
                    }
                    callback.onName(value);
                    prompt.dismiss();
                });
            });
            prompt.show();
        }

        private static int contextHeight(Context context) {
            return context.getResources().getDisplayMetrics().heightPixels;
        }

        private interface NameCallback { void onName(String name); }

        private final class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {
            @Override
            public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
                CardView card = new CardView(ui);
                card.setRadius(ArtemisEditorUi.dp(ui, 9));
                card.setCardElevation(0);
                card.setUseCompatPadding(false);
                card.setCardBackgroundColor(ArtemisEditorUi.SURFACE_RAISED);

                LinearLayout row = new LinearLayout(ui);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(ArtemisEditorUi.dp(ui, 4), 0, ArtemisEditorUi.dp(ui, 2), 0);
                card.addView(row, new CardView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 56)));

                TextView drag = new TextView(ui);
                drag.setText("≡");
                drag.setTextColor(0xFF94949B);
                drag.setTextSize(21f);
                drag.setGravity(Gravity.CENTER);
                drag.setContentDescription(ui.getString(R.string.artemis_quick_menu_reorder_description));
                row.addView(drag, new LinearLayout.LayoutParams(
                        ArtemisEditorUi.dp(ui, 44), ArtemisEditorUi.dp(ui, 52)));

                LinearLayout text = new LinearLayout(ui);
                text.setOrientation(LinearLayout.VERTICAL);
                text.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = ArtemisEditorUi.label(ui, "", 14.5f, ArtemisEditorUi.TEXT_PRIMARY);
                name.setMaxLines(1);
                TextView type = ArtemisEditorUi.label(ui, "", 11.5f, ArtemisEditorUi.TEXT_SECONDARY);
                type.setMaxLines(1);
                text.addView(name);
                text.addView(type);
                row.addView(text, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, 1f));

                TextView open = new TextView(ui);
                open.setText("›");
                open.setTextColor(ArtemisEditorUi.TEXT_SECONDARY);
                open.setTextSize(22f);
                open.setGravity(Gravity.CENTER);
                row.addView(open, new LinearLayout.LayoutParams(
                        ArtemisEditorUi.dp(ui, 34), ArtemisEditorUi.dp(ui, 52)));

                TextView remove = new TextView(ui);
                remove.setText("×");
                remove.setTextColor(ArtemisEditorUi.DANGER);
                remove.setTextSize(21f);
                remove.setGravity(Gravity.CENTER);
                remove.setContentDescription(ui.getString(R.string.artemis_quick_menu_remove_description));
                remove.setBackground(ArtemisEditorUi.rounded(ui, 0x12FFFFFF, 8, 0, 0));
                row.addView(remove, new LinearLayout.LayoutParams(
                        ArtemisEditorUi.dp(ui, 44), ArtemisEditorUi.dp(ui, 44)));

                RecyclerView.LayoutParams outer = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ArtemisEditorUi.dp(ui, 60));
                outer.setMargins(0, ArtemisEditorUi.dp(ui, 2), 0, ArtemisEditorUi.dp(ui, 2));
                card.setLayoutParams(outer);
                return new Holder(card, name, type, open, remove);
            }

            @Override
            public void onBindViewHolder(Holder holder, int position) {
                QuickMenuConfig.Node node = currentPage().items.get(position);
                if (node.isPage()) {
                    holder.name.setText(node.page.title);
                    holder.type.setText(ui.getResources().getQuantityString(
                            R.plurals.artemis_quick_menu_subpage_summary, node.page.items.size(),
                            node.page.items.size()));
                    holder.open.setVisibility(View.VISIBLE);
                    holder.itemView.setOnClickListener(v -> openPage(node.page));
                } else {
                    StreamActionRegistry.ActionDefinition action = StreamActionRegistry.find(node.actionId);
                    holder.name.setText(action == null
                            ? node.actionId : ui.getString(action.labelResId));
                    holder.type.setText(action == null
                            ? ui.getString(R.string.artemis_quick_menu_unavailable_action)
                            : ui.getString(action.categoryResId));
                    holder.open.setVisibility(View.INVISIBLE);
                    holder.itemView.setOnClickListener(null);
                }
                holder.remove.setOnClickListener(v -> {
                    int index = holder.getBindingAdapterPosition();
                    if (QuickMenuConfig.remove(currentPage(), index)) {
                        notifyItemRemoved(index);
                        save();
                        emptyState.setVisibility(currentPage().items.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }

            @Override public int getItemCount() { return currentPage().items.size(); }

            final class Holder extends RecyclerView.ViewHolder {
                final TextView name;
                final TextView type;
                final TextView open;
                final TextView remove;

                Holder(View itemView, TextView name, TextView type, TextView open, TextView remove) {
                    super(itemView);
                    this.name = name;
                    this.type = type;
                    this.open = open;
                    this.remove = remove;
                }
            }
        }
    }
}
