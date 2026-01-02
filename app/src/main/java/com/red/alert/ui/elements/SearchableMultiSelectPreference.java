package com.red.alert.ui.elements;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.red.alert.R;
import com.red.alert.logic.communication.broadcasts.LocationSelectionEvents;
import com.red.alert.ui.dialogs.AlertDialogBuilder;
import com.red.alert.utils.communication.Broadcasts;
import com.red.alert.utils.formatting.StringUtils;
import com.red.alert.utils.metadata.LocationData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class SearchableMultiSelectPreference extends ListPreference {
    private static final String DEFAULT_SEPARATOR = "OV=I=XseparatorX=I=VO";
    private String separator;
    private String checkAllKey = null;
    private boolean[] mClickedDialogEntryIndices;
    private AlertDialog mDialog;
    private static Context mContext;

    public SearchableMultiSelectPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SearchableMultiSelectPreference);
        checkAllKey = a.getString(R.styleable.SearchableMultiSelectPreference_checkAll);
        String s = a.getString(R.styleable.SearchableMultiSelectPreference_separator);
        if (s != null) {
            separator = s;
        } else {
            separator = DEFAULT_SEPARATOR;
        }
        a.recycle();

        if (getEntries() != null) {
            mClickedDialogEntryIndices = new boolean[getEntries().length];
        }
    }

    protected static String join(Iterable<? extends Object> pColl, String separator) {
        Iterator<? extends Object> oIter;
        if (pColl == null || (!(oIter = pColl.iterator()).hasNext()))
            return "";
        StringBuilder oBuilder = new StringBuilder(String.valueOf(oIter.next()));
        while (oIter.hasNext())
            oBuilder.append(separator).append(oIter.next());
        return oBuilder.toString();
    }

    public static boolean contains(String straw, String haystack, String separator) {
        if (separator == null) {
            separator = DEFAULT_SEPARATOR;
        }
        String[] vals = haystack.split(Pattern.quote(separator));
        for (int i = 0; i < vals.length; i++) {
            if (vals[i].equals(straw)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setEntries(CharSequence[] entries) {
        super.setEntries(entries);
        if (entries == null) {
            return;
        }
        mClickedDialogEntryIndices = new boolean[entries.length];
    }

    @Override
    protected void onClick() {
        showSelectionDialog();
    }

    public void showDialog() {
        showSelectionDialog();
    }

    private void showSelectionDialog() {
        final CharSequence[] entries = getEntries();
        final CharSequence[] entryValues = getEntryValues();
        final String[] zoneNames = LocationData.getAllCityZones(getContext());

        if (entries == null || entryValues == null || entries.length != entryValues.length) {
            throw new IllegalStateException(
                    "ListPreference requires an entries array and an entryValues array which are both the same length");
        }

        restoreCheckedEntries();

        final List<ListItemWithIndex> allItems = new ArrayList<>();
        final List<ListItemWithIndex> filteredItems = new ArrayList<>();
        final boolean isCitySelection = entryValues.length == zoneNames.length;

        for (int i = 0; i < entries.length; i++) {
            boolean isDefault = i == 0;
            boolean checked = mClickedDialogEntryIndices[i];
            String zone = isCitySelection ? zoneNames[i] : null;
            String value = entries[i].toString();

            if (value.equals("Select All") || value.equals("בחר הכל")) {
                value = getContext().getResources().getStringArray(R.array.zoneNames)[0];
            }

            ListItemWithIndex listItemWithIndex = new ListItemWithIndex(i, value, zone, checked, isDefault);
            allItems.add(listItemWithIndex);
            filteredItems.add(listItemWithIndex);
        }

        if (isCitySelection) {
            Collections.sort(filteredItems);
        }

        final ArrayAdapter<ListItemWithIndex> objectsAdapter = new ArrayAdapter<ListItemWithIndex>(getContext(), R.layout.multiselect_checkable, filteredItems) {
            @Override
            public View getView(final int position, View v, final ViewGroup parent) {
                final ViewHolder viewHolder;
                if (v == null) {
                    LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    v = vi.inflate(R.layout.multiselect_checkable, null);
                    viewHolder = new ViewHolder();
                    viewHolder.label = v.findViewById(R.id.label);
                    viewHolder.subLabel = v.findViewById(R.id.subLabel);
                    viewHolder.checkbox = v.findViewById(R.id.checkbox);
                    v.setTag(viewHolder);
                } else {
                    viewHolder = (ViewHolder) v.getTag();
                }

                final ListItemWithIndex item = filteredItems.get(position);
                final String name = item.value;
                final String code = item.zone;

                viewHolder.label.setText(name);
                viewHolder.subLabel.setText(code);

                if (StringUtils.stringIsNullOrEmpty(code) || code.equals(getContext().getString(R.string.all))) {
                    viewHolder.subLabel.setVisibility(View.GONE);
                } else {
                    viewHolder.subLabel.setVisibility(View.VISIBLE);
                }

                viewHolder.checkbox.setOnCheckedChangeListener(null);
                viewHolder.checkbox.setChecked(mClickedDialogEntryIndices[getRealPosition(name)]);

                v.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        viewHolder.checkbox.setChecked(!viewHolder.checkbox.isChecked());
                    }
                });

                viewHolder.checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean val) {
                        if (mClickedDialogEntryIndices[0]) {
                            ((ListView) parent).setItemChecked(0, false);
                            mClickedDialogEntryIndices[0] = false;
                        }

                        int realPosition = getRealPosition(name);
                        if (isCheckAllValue(realPosition)) {
                            checkAll(mDialog, val);
                        }

                        mClickedDialogEntryIndices[realPosition] = val;
                        viewHolder.checkbox.setChecked(val);
                        item.checked = val;
                        canCheckAll();

                        if (mDialog != null) {
                            ListView lv = mDialog.findViewById(R.id.searchListView);
                            if (lv != null) lv.invalidateViews();
                        }
                    }
                });

                return v;
            }

            void canCheckAll() {
                for (int i = 1; i < mClickedDialogEntryIndices.length; i++) {
                    if (!mClickedDialogEntryIndices[i]) return;
                }
                checkAll(mDialog, true);
            }

            int getRealPosition(String name) {
                for (int i = 0; i < entries.length; i++) {
                    if (entries[i].equals(name)) return i;
                }
                return 0;
            }

            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        filteredItems.clear();
                        filteredItems.addAll((List<ListItemWithIndex>) results.values);
                        if (isCitySelection) Collections.sort(filteredItems);
                        notifyDataSetChanged();
                    }

                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        String filterString = constraint.toString().toLowerCase();
                        ArrayList<ListItemWithIndex> list = new ArrayList<>();
                        for (ListItemWithIndex obj : allItems) {
                            String objStr = obj.toString().toLowerCase();
                            if (StringUtils.stringIsNullOrEmpty(filterString)
                                    || objStr.contains(filterString)
                                    || (obj.zone != null && obj.zone.toLowerCase().contains(filterString))) {
                                list.add(obj);
                            }
                        }
                        results.values = list;
                        results.count = list.size();
                        return results;
                    }
                };
            }
        };

        LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout parent = (LinearLayout) vi.inflate(R.layout.multiselect_search_box, null);
        final EditText searchEditText = parent.findViewById(R.id.searchEditText);

        searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                InputMethodManager inputManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(textView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                return false;
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
            @Override
            public void afterTextChanged(Editable arg0) {
                objectsAdapter.getFilter().filter(searchEditText.getText());
            }
        });

        final ListView listView = parent.findViewById(R.id.searchListView);
        listView.setAdapter(objectsAdapter);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(getTitle());
        builder.setView(parent);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, null);

        mDialog = builder.create();
        mDialog.show();

        // Override positive button click to enforce limit
        Button positiveButton = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            positiveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ArrayList<String> values = new ArrayList<>();
                    for (int i = 0; i < entryValues.length; i++) {
                        if (mClickedDialogEntryIndices[i]) {
                            String val = (String) entryValues[i];
                            if (checkAllKey == null || !val.equals(checkAllKey)) {
                                values.add(val);
                            }
                        }
                    }

                    if (values.size() > 100 && !mClickedDialogEntryIndices[0]) {
                        AlertDialogBuilder.showGenericDialog(getContext().getString(R.string.error), getContext().getString(R.string.citySelectionLimitError), getContext().getString(R.string.okay), null, false, getContext(), null);
                        return;
                    }

                    onDialogClosed(true);
                    mDialog.dismiss();
                }
            });
        }
    }

    private boolean isCheckAllValue(int which) {
        CharSequence[] entryValues = getEntryValues();
        if (checkAllKey != null) {
            return entryValues[which].equals(checkAllKey);
        }
        return false;
    }

    private void checkAll(AlertDialog dialog, boolean val) {
        if (dialog == null) return;
        ListView lv = dialog.findViewById(R.id.searchListView);
        if (lv == null) return;
        int size = lv.getCount();
        for (int i = 0; i < size; i++) {
            lv.setItemChecked(i, val);
            mClickedDialogEntryIndices[i] = val;
        }
    }

    public String[] parseStoredValue(CharSequence val) {
        if ("".equals(val)) return null;
        return ((String) val).split(Pattern.quote(separator));
    }

    private void restoreCheckedEntries() {
        CharSequence[] entryValues = getEntryValues();
        boolean checkAll = false;
        String[] vals = parseStoredValue(getValue());

        if (vals == null) {
            checkAll = true;
            vals = new String[]{""};
        }

        List<String> valuesList = Arrays.asList(vals);
        if (valuesList.size() == 1 && valuesList.get(0).equals(getContext().getString(R.string.all))) {
            checkAll = true;
        }

        for (int i = 0; i < entryValues.length; i++) {
            CharSequence entry = entryValues[i];
            mClickedDialogEntryIndices[i] = valuesList.contains(entry) || checkAll;
        }
    }

    protected void onDialogClosed(boolean positiveResult) {
        ArrayList<String> values = new ArrayList<>();
        CharSequence[] entryValues = getEntryValues();

        if (positiveResult && entryValues != null) {
            if (entryValues.length > 1 && entryValues[0].equals(getContext().getString(R.string.all)) && mClickedDialogEntryIndices[0]) {
                setValue(join(values, separator));
                Broadcasts.publish(getContext(), LocationSelectionEvents.LOCATIONS_UPDATED);
                return;
            }

            for (int i = 0; i < entryValues.length; i++) {
                if (mClickedDialogEntryIndices[i]) {
                    String val = (String) entryValues[i];
                    if (checkAllKey == null || !val.equals(checkAllKey)) {
                        values.add(val);
                    }
                }
            }

            if (values.size() == 0) {
                values.add(getContext().getString(R.string.nullString));
            }
            setValue(join(values, separator));
        }

        if (positiveResult) {
            Broadcasts.publish(getContext(), LocationSelectionEvents.LOCATIONS_UPDATED);
        }
    }

    public static class ViewHolder {
        public TextView label;
        public TextView subLabel;
        public CheckBox checkbox;
    }

    private static final class ListItemWithIndex implements Comparable {
        public boolean checked;
        public boolean isDefault;
        public int index;
        public String value;
        public String zone;

        public ListItemWithIndex(int index, String value, String zone, boolean checked, boolean isDefault) {
            this.index = index;
            this.value = value;
            this.zone = zone;
            this.checked = checked;
            this.isDefault = isDefault;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public int compareTo(Object o) {
            ListItemWithIndex compare = (ListItemWithIndex) o;
            if (isDefault) return -1;
            if (compare.isDefault) return 1;
            if (checked && !compare.checked) return -1;
            if (!checked && compare.checked) return 1;
            return value.compareTo(compare.value);
        }
    }
}
