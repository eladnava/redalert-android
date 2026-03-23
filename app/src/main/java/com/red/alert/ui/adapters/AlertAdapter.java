package com.red.alert.ui.adapters;

import android.content.Context;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.red.alert.R;
import com.red.alert.config.ThreatTypes;
import com.red.alert.model.Alert;
import com.red.alert.utils.metadata.LocationData;
import com.red.alert.utils.ui.TextViewUtil;

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
            viewHolder.cities = convertView.findViewById(R.id.alertCities);
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
            viewHolder.cities.setText(Html.fromHtml(alert.localizedCity));

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

            // Set zone names
            viewHolder.desc.setText(desc);

            // Set title
            viewHolder.title.setText(alert.localizedTitle);

            // Show user-friendly time
            viewHolder.time.setText(alert.dateString);

            // Custom threat icons
            viewHolder.image.setImageResource(LocationData.getThreatDrawable(alert.threat));

            // Check if expanded
            if (alert.isExpanded) {
                // Disable ellipsis (show all)
                viewHolder.cities.setMaxLines(Integer.MAX_VALUE);
                viewHolder.cities.setEllipsize(null);
            }
            else {
                // Max 3 lines with ellipsis (...)
                viewHolder.cities.setMaxLines(3);
                viewHolder.cities.setEllipsize(TextUtils.TruncateAt.END);
            }

            // Title click event
            viewHolder.cities.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Check if title is ellipsized (more than 3 lines of alert cities)
                    if (TextViewUtil.isEllipsized(viewHolder.cities)) {
                        // Disable ellipsis (show all cities)
                        viewHolder.cities.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                            viewHolder.cities.setMaxLines(Integer.MAX_VALUE);
                            viewHolder.cities.setEllipsize(null);

                            viewHolder.cities.animate().alpha(1f).setDuration(200).start();
                        }).start();

                        // Toggle expanded flag
                        alert.isExpanded = true;
                    } else {
                        // Already expanded?
                        if (alert.isExpanded) {
                            // Disable ellipsis (show all cities)
                            viewHolder.cities.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                                // Max 3 lines with ellipsis (...)
                                viewHolder.cities.setMaxLines(3);
                                viewHolder.cities.setEllipsize(TextUtils.TruncateAt.END);

                                viewHolder.cities.animate().alpha(1f).setDuration(200).start();
                            }).start();

                            // Toggle expanded flag
                            alert.isExpanded = false;
                        } else {
                            // Open map
                            ((ListView) parent).performItemClick(view, position, getItemId(position));
                        }
                    }
                }
            });
        }

        // Return the view
        return convertView;
    }

    public static class ViewHolder {
        public TextView time;
        public TextView title;
        public TextView cities;
        public TextView desc;
        public ImageView image;
    }
}