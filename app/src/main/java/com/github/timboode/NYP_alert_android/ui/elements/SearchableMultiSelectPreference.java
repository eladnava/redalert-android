package com.github.timboode.NYP_alert_android.ui.elements;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Handler;
import android.preference.ListPreference;
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

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.logic.communication.broadcasts.LocationSelectionEvents;
import com.github.timboode.NYP_alert_android.ui.dialogs.AlertDialogBuilder;
import com.github.timboode.NYP_alert_android.utils.communication.Broadcasts;
import com.github.timboode.NYP_alert_android.utils.formatting.StringUtils;

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

    // Constructor
    public SearchableMultiSelectPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SearchableMultiSelectPreference);
        checkAllKey = a.getString(R.styleable.SearchableMultiSelectPreference_checkAll);
        String s = a.getString(R.styleable.SearchableMultiSelectPreference_separator);
        if (s != null) {
            separator = s;
        }
        else {
            separator = DEFAULT_SEPARATOR;
        }

        if (getEntries() != null) {
            // Initialize the array of boolean to the same size as number of entries
            mClickedDialogEntryIndices = new boolean[getEntries().length];
        }
    }

    // Credits to kurellajunior on this post http://snippets.dzone.com/posts/show/91
    protected static String join(Iterable<? extends Object> pColl, String separator) {
        Iterator<? extends Object> oIter;
        if (pColl == null || (!(oIter = pColl.iterator()).hasNext()))
            return "";
        StringBuilder oBuilder = new StringBuilder(String.valueOf(oIter.next()));
        while (oIter.hasNext())
            oBuilder.append(separator).append(oIter.next());
        return oBuilder.toString();
    }

    /**
     * @param straw     String to be found
     * @param haystack  Raw string that can be read direct from preferences
     * @param separator Separator string. If null, static default separator will be used
     * @return boolean True if the straw was found in the haystack
     */
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

        // No entries?
        if (entries == null) {
            return;
        }

        // Initialize the array of boolean to the same size as number of entries
        mClickedDialogEntryIndices = new boolean[entries.length];
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        final CharSequence[] entries = getEntries();
        final CharSequence[] entryValues = getEntryValues();

        if (entries == null || entryValues == null || entries.length != entryValues.length) {
            throw new IllegalStateException(
                    "ListPreference requires an entries array and an entryValues array which are both the same length");
        }

        restoreCheckedEntries();

        final List<ListItemWithIndex> allItems = new ArrayList<ListItemWithIndex>();
        final List<ListItemWithIndex> filteredItems = new ArrayList<ListItemWithIndex>();

        for (int i = 0; i < entries.length; i++) {
            final Object obj = entries[i];

            boolean isDefault = i == 0;
            boolean checked = mClickedDialogEntryIndices[i];

            String zone = null;

            String value = obj.toString();

            // Localize "select all" preference
            if (value.equals("Select All") || value.equals("בחר הכל")) {
                value = getContext().getResources().getStringArray(R.array.zoneNames)[0];
            }

            final ListItemWithIndex listItemWithIndex = new ListItemWithIndex(i, value, zone, checked, isDefault);
            allItems.add(listItemWithIndex);
            filteredItems.add(listItemWithIndex);
        }

        final ArrayAdapter<ListItemWithIndex> objectsAdapter = new ArrayAdapter<ListItemWithIndex>(getContext(), R.layout.multiselect_checkable, filteredItems) {
            @Override
            public View getView(final int position, View v, final ViewGroup parent) {
                final ViewHolder viewHolder;

                if (v == null) {
                    LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    v = vi.inflate(R.layout.multiselect_checkable, null);

                    viewHolder = new ViewHolder();

                    viewHolder.label = (TextView) v.findViewById(R.id.label);
                    viewHolder.subLabel = (TextView) v.findViewById(R.id.subLabel);
                    viewHolder.checkbox = (CheckBox) v.findViewById(R.id.checkbox);

                    v.setTag(viewHolder);
                }
                else {
                    viewHolder = (ViewHolder) v.getTag();
                }


                final ListItemWithIndex item = filteredItems.get(position);
                final String name = item.value;
                final String code = item.zone;

                viewHolder.label.setText(name);
                viewHolder.subLabel.setText(code);

                if (StringUtils.stringIsNullOrEmpty(code) || code.equals(getContext().getString(R.string.all))) {
                    viewHolder.subLabel.setVisibility(View.GONE);
                }
                else {
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
                        // Checked all?

                        if (mClickedDialogEntryIndices[0] == true) {
                            ((ListView) parent).setItemChecked(0, false);
                            mClickedDialogEntryIndices[0] = false;
                        }

                        int realPosition = getRealPosition(name);

                        if (isCheckAllValue(realPosition) == true) {
                            checkAll(getDialog(), val);
                        }

                        mClickedDialogEntryIndices[realPosition] = val;
                        viewHolder.checkbox.setChecked(val);

                        item.checked = val;

                        canCheckAll();

                        // Null safety check (in case dialog was closed)
                        if (getDialog() != null) {
                            // Invalidate list views (to update checkbox display)
                            ListView lv = getDialog().findViewById(R.id.searchListView);
                            lv.invalidateViews();
                        }
                    }
                });

                return v;
            }

            void canCheckAll() {
                for (int i = 1; i < mClickedDialogEntryIndices.length; i++) {
                    if (!mClickedDialogEntryIndices[i]) {
                        return;
                    }
                }

                // Still here? Check all
                checkAll(getDialog(), true);

                return;
            }

            int getRealPosition(String name) {
                for (int i = 0; i < entries.length; i++) {
                    if (entries[i].equals(name)) {
                        return i;
                    }
                }

                return 0;
            }

            @Override
            public Filter getFilter() {
                return new Filter() {
                    @SuppressWarnings("unchecked")
                    @Override
                    protected void publishResults(final CharSequence constraint, final FilterResults results) {
                        filteredItems.clear();
                        filteredItems.addAll((List<ListItemWithIndex>) results.values);

                        notifyDataSetChanged();
                    }

                    @Override
                    protected Filter.FilterResults performFiltering(final CharSequence constraint) {
                        final FilterResults results = new FilterResults();

                        final String filterString = constraint.toString().toLowerCase();
                        final ArrayList<ListItemWithIndex> list = new ArrayList<ListItemWithIndex>();
                        for (final ListItemWithIndex obj : allItems) {
                            final String objStr = obj.toString().toLowerCase();
                            if (StringUtils.stringIsNullOrEmpty(filterString)
                                    || objStr.contains(filterString)
                                    || (obj.zone != null && obj.zone.toLowerCase().contains(filterString))
                                    ) {
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
        final EditText searchEditText = (EditText) parent.findViewById(R.id.searchEditText);

        searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                InputMethodManager inputManager = (InputMethodManager)
                        getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

                inputManager.hideSoftInputFromWindow(textView.getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);

                return false;
            }
        });
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(final CharSequence arg0, final int arg1, final int arg2, final int arg3) {
            }

            @Override
            public void beforeTextChanged(final CharSequence arg0, final int arg1, final int arg2, final int arg3) {
            }

            @Override
            public void afterTextChanged(final Editable arg0) {
                objectsAdapter.getFilter().filter(searchEditText.getText());
            }
        });

        final ListView listView = (ListView) parent.findViewById(R.id.searchListView);
        listView.setAdapter(objectsAdapter);

        builder.setView(parent);

        // Delay invocation by 300ms for dialog to be created
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Dialog created?
                if (getDialog() != null) {
                    // Get positive button
                    Button button = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);

                    // Positive button created?
                    if (button != null) {
                        // Override onClick listener to enforce max item selection limit
                        button.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Get all possible values
                                CharSequence[] entryValues = getEntryValues();

                                // ArrayList of selected values
                                ArrayList<String> values = new ArrayList<String>();

                                // Cities selected?
                                for (int i = 0; i < entryValues.length; i++) {
                                    if (mClickedDialogEntryIndices[i] == true) {
                                        // Don't save the state of check all option - if any
                                        String val = (String) entryValues[i];
                                        if (checkAllKey == null || (val.equals(checkAllKey) == false)) {
                                            values.add(val);
                                        }
                                    }
                                }

                                // Exceeded limit of 100 items?
                                if (values.size() > 100) {
                                    // Didn't check all?
                                    if (mClickedDialogEntryIndices[0] != true) {
                                        // Show error dialog
                                        AlertDialogBuilder.showGenericDialog(getContext().getString(R.string.error), getContext().getString(R.string.citySelectionLimitError), getContext().getString(R.string.okay), null, false, getContext(), null);
                                        return;
                                    }
                                }

                                // Less than or equal to 100 selected
                                // Set button clicked as positive
                                SearchableMultiSelectPreference.this.onClick(getDialog(), AlertDialog.BUTTON_POSITIVE);

                                // Dismiss dialog
                                getDialog().dismiss();
                            }
                        });
                    }
                }
            }
        }, 300);

        //builder.setView()
        /*builder.setMultiChoiceItems(entries, mClickedDialogEntryIndices,
                new DialogInterface.OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface dialog, int which, boolean val) {
                        if( isCheckAllValue( which ) == true ) {
                            checkAll( dialog, val );
                        }
                        mClickedDialogEntryIndices[which] = val;
                    }
                });  */
    }

    private boolean isCheckAllValue(int which) {
        final CharSequence[] entryValues = getEntryValues();
        if (checkAllKey != null) {
            return entryValues[which].equals(checkAllKey);
        }
        return false;
    }

    private void checkAll(DialogInterface dialog, boolean val) {
        if (dialog == null) {
            return;
        }

        ListView lv = (ListView) ((AlertDialog) dialog).findViewById(R.id.searchListView);
        int size = lv.getCount();
        for (int i = 0; i < size; i++) {
            lv.setItemChecked(i, val);
            mClickedDialogEntryIndices[i] = val;
        }
    }

    public String[] parseStoredValue(CharSequence val) {
        if ("".equals(val)) {
            return null;
        }
        else {
            return ((String) val).split(Pattern.quote(separator));
        }
    }

    private void restoreCheckedEntries() {
        CharSequence[] entryValues = getEntryValues();

        boolean checkAll = false;

        // Explode the string read in sharedpreferences
        String[] vals = parseStoredValue(getValue());

        if (vals == null) {
            checkAll = true;
            vals = new String[]{""};
        }

        List<String> valuesList = Arrays.asList(vals);

        if (valuesList.size() == 1) {
            if (valuesList.get(0).equals(getContext().getString(R.string.all))) {
                checkAll = true;
            }
        }

        for (int i = 0; i < entryValues.length; i++) {
            CharSequence entry = entryValues[i];
            if (valuesList.contains(entry) || checkAll) {
                mClickedDialogEntryIndices[i] = true;
            }
            else {
                mClickedDialogEntryIndices[i] = false;
            }
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        ArrayList<String> values = new ArrayList<String>();

        CharSequence[] entryValues = getEntryValues();

        if (positiveResult && entryValues != null) {
            // Check all selected?

            if (entryValues.length > 1) {
                if (entryValues[0].equals(getContext().getString(R.string.all))) {
                    if (mClickedDialogEntryIndices[0] == true) {
                        // Set empty value
                        setValue(join(values, separator));
                        Broadcasts.publish(getContext(), LocationSelectionEvents.LOCATIONS_UPDATED);
                        return;
                    }
                }
            }

            // Cities selected?
            for (int i = 0; i < entryValues.length; i++) {
                if (mClickedDialogEntryIndices[i] == true) {
                    // Don't save the state of check all option - if any
                    String val = (String) entryValues[i];
                    if (checkAllKey == null || (val.equals(checkAllKey) == false)) {
                        values.add(val);
                    }
                }
            }

            // Nothing selected
            if (values.size() == 0) {
                values.add(getContext().getString(R.string.nullString));
            }

            //if (callChangeListener(value)) {
            setValue(join(values, separator));
            //}
        }

        if (positiveResult) {
            Broadcasts.publish(getContext(), LocationSelectionEvents.LOCATIONS_UPDATED);
        }
    }

    public void showDialog() {
        super.showDialog(null);
    }

    public static class ViewHolder {
        public TextView label;
        public TextView subLabel;
        public CheckBox checkbox;
    }

    // TODO: Would like to keep this static but separator then needs to be put in by hand or use default separator "OV=I=XseparatorX=I=VO"...

    private static final class ListItemWithIndex implements Comparable {
        public boolean checked;
        public boolean isDefault;

        public int index;
        public String value;
        public String zone;

        public ListItemWithIndex(int index, String value, String zone, boolean checked, boolean isDefault) {
            super();
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

            if (isDefault) {
                return -1;
            }

            if (compare.isDefault) {
                return 1;
            }

            if (checked && !compare.checked) {
                return -1;
            }

            if (!checked && compare.checked) {
                return 1;
            }

            if (checked && compare.checked) {
                return value.compareTo(compare.value);
            }

            return value.compareTo(compare.value);
        }
    }
}
