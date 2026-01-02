package com.red.alert.ui.adapters;

import android.app.Activity;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.red.alert.R;
import com.red.alert.config.ThreatTypes;
import com.red.alert.model.Alert;
import com.red.alert.utils.metadata.LocationData;

import java.util.List;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.ViewHolder> {
    Activity mActivity;
    List<Alert> mAlerts;
    OnItemClickListener mOnItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(Alert alert, int position, View cardView);
    }

    public AlertAdapter(Activity activity, List<Alert> alerts) {
        // Set data members
        this.mAlerts = alerts;
        this.mActivity = activity;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mOnItemClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the alert card layout
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.alert_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Retrieve the alert
        Alert alert = mAlerts.get(position);

        // Got an alert?
        if (alert != null) {
            // Set localized city name (HTML for using <b> tag for selected cities)
            holder.title.setText(Html.fromHtml(alert.localizedCity));

            // System alert?
            if (alert.threat.equals(ThreatTypes.SYSTEM)) {
                // Set desc to "system message"
                alert.desc = alert.localizedThreat;

                // Hide time
                holder.time.setVisibility(View.GONE);
            } else {
                // Show time
                holder.time.setVisibility(View.VISIBLE);
            }

            // Set area names
            holder.desc.setText(alert.desc);

            // Show alert type & user-friendly time
            holder.time.setText(alert.localizedThreat + " • " + alert.dateString);

            // Custom threat icons
            holder.image.setImageResource(LocationData.getThreatDrawable(alert.threat));

            // Set unique transition name for the card view
            holder.itemView.setTransitionName("alert_card_transition_" + position);

            // Set click listener
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mOnItemClickListener != null) {
                        mOnItemClickListener.onItemClick(alert, holder.getAdapterPosition(), v);
                    }
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mAlerts != null ? mAlerts.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        // IDs are unique based on position
        return position;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView time;
        public TextView title;
        public TextView desc;
        public ImageView image;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            time = itemView.findViewById(R.id.time);
            title = itemView.findViewById(R.id.alertTitle);
            desc = itemView.findViewById(R.id.alertDesc);
            image = itemView.findViewById(R.id.image);
        }
    }
}