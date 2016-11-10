package com.red.alert.ui.adapters;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.red.alert.R;
import com.red.alert.model.Alert;

import java.util.List;

public class AlertAdapter extends ArrayAdapter<Alert> {
    Activity mActivity;
    List<Alert> mAlerts;

    public AlertAdapter(Activity activity, List<Alert> alerts) {
        // Call super function with the item layout and initial alerts
        super(activity, R.layout.alert, alerts);

        // Set data members
        this.mAlerts = alerts;
        this.mActivity = activity;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Prepare view holder
        ViewHolder viewHolder;

        // Don't have a cached view?
        if (convertView == null) {
            // Get inflater service
            LayoutInflater layoutInflater = (LayoutInflater) mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            // Inflate the alert layout
            convertView = layoutInflater.inflate(R.layout.alert, null);

            // Create a new view holder
            viewHolder = new ViewHolder();

            // Cache the view resources
            viewHolder.time = (TextView) convertView.findViewById(R.id.time);
            viewHolder.areaCode = (TextView) convertView.findViewById(R.id.areaCode);
            viewHolder.areaNames = (TextView) convertView.findViewById(R.id.areaNames);

            // Store it in tag
            convertView.setTag(viewHolder);
        }
        else {
            // Get cached convert view
            viewHolder = (ViewHolder) convertView.getTag();
        }

        // Retrieve the alert
        Alert alert = mAlerts.get(position);

        // Got an alert?
        if (alert != null) {
            // Set area localized zone
            viewHolder.areaCode.setText(alert.localizedZone);

            // Set area names
            viewHolder.areaNames.setText(alert.cities);

            // Set user-friendly time
            viewHolder.time.setText(alert.dateString);
        }

        // Return the view
        return convertView;
    }

    public boolean hasStableIds() {
        // IDs are unique
        return true;
    }

    public static class ViewHolder {
        public TextView time;
        public TextView areaCode;
        public TextView areaNames;
    }
}