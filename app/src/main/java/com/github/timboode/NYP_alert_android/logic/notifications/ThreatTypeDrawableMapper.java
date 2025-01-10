package com.github.timboode.NYP_alert_android.logic.notifications;

import com.github.timboode.NYP_alert_android.R;
import com.github.timboode.NYP_alert_android.config.ThreatTypes;

public class ThreatTypeDrawableMapper {
    public static int ThreatTypeToDrawable(String threatType) {
        switch(threatType)
        {
            case ThreatTypes.MISSILES:
                return R.drawable.ic_missiles;

            case ThreatTypes.RADIOLOGICAL_EVENT:
                return R.drawable.ic_radiological_event;

            case ThreatTypes.HOSTILE_AIRCRAFT_INTRUSION:
                return R.drawable.ic_hostile_aircraft_intrusion;

            case ThreatTypes.TEST:
                return R.drawable.ic_redalert;

            default:
                return R.drawable.ic_earthquake;
        }
    }
}
