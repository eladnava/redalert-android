package com.red.alert.ui.adapters;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.red.alert.R;
import com.red.alert.config.ThreatTypes;
import com.red.alert.model.Alert;
import com.red.alert.utils.metadata.LocationData;

import java.util.List;

public class AlertAdapter extends ArrayAdapter<Alert> {
    public AlertAdapter(Context context, List<Alert> alerts) {
        // Call super function with the item layout and initial alerts
        super(context, R.layout.alert, alerts);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Prepare view holder
        ViewHolder viewHolder;

        // Don't have a cached view?
        if (convertView == null) {
            // Get inflater service
            LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());

            // Inflate the alert layout (recycle views)
            convertView = layoutInflater.inflate(R.layout.alert, parent, false);

            // Create a new view holder
            viewHolder = new ViewHolder();

            // Cache the view resources
            viewHolder.time = convertView.findViewById(R.id.time);
            viewHolder.title = convertView.findViewById(R.id.alertTitle);
            viewHolder.desc = convertView.findViewById(R.id.alertDesc);
            viewHolder.image = convertView.findViewById(R.id.image);

            // Store it in tag
            convertView.setTag(viewHolder);
        }
        else {
            // Get cached convert view
            viewHolder = (ViewHolder) convertView.getTag();
        }

        // Retrieve the alert
        Alert alert = getItem(position);

        // Got an alert?
        if (alert != null) {
            // Set localized city name (HTML for using <b> tag for selected cities)
            viewHolder.title.setText(alert.localizedCityHtml);

            // Get alert desc
            String desc = alert.desc;

            // System alert?
            if (ThreatTypes.SYSTEM.equals(alert.threat)) {
                // Set desc to "system message"
                desc = alert.localizedThreat;

                // Hide time
                viewHolder.time.setVisibility(View.GONE);
            }
            else {
                // Show time
                viewHolder.time.setVisibility(View.VISIBLE);
            }

            // Set area names
            viewHolder.desc.setText(desc);

            // Expandable alert?
            if (alert.isExpandableAlert) {
                // Show user-friendly time (don't include threat as it's already included in the localized city)
                viewHolder.time.setText(alert.dateString);
            } else {
                // Show threat type & user-friendly time
                viewHolder.time.setText(alert.localizedThreat + " • " + alert.dateString);
            }

            // Custom threat icons
            viewHolder.image.setImageResource(LocationData.getThreatDrawable(alert.threat));

            // Check if expanded
            if (alert.isExpanded) {
                // Disable ellipsis (show all)
                viewHolder.title.setMaxLines(Integer.MAX_VALUE);
                viewHolder.title.setEllipsize(null);
            }
            else {
                // Max 5 lines with ellipsis (...)
                viewHolder.title.setMaxLines(5);
                viewHolder.title.setEllipsize(TextUtils.TruncateAt.END);
            }
        }

        // Return the view
        return convertView;
    }

    public static class ViewHolder {
        public TextView time;
        public TextView title;
        public TextView desc;
        public ImageView image;
    }
}