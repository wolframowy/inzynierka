package inz.util;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;

/**
 * Created by Kuba on 06/05/2017.
 * LatLng but parcelable
 */

public class ParcelableLatLng implements Parcelable, Serializable{
    public final double	latitude;
    public final double	longitude;

    public ParcelableLatLng(double latitude, double longitude){
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public ParcelableLatLng(LatLng ll) {
        latitude = ll.latitude;
        longitude = ll.longitude;
    }

    private ParcelableLatLng(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
    }

    public static final Creator<ParcelableLatLng> CREATOR = new Creator<ParcelableLatLng>() {
        @Override
        public ParcelableLatLng createFromParcel(Parcel in) {
            return new ParcelableLatLng(in);
        }

        @Override
        public ParcelableLatLng[] newArray(int size) {
            return new ParcelableLatLng[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble( latitude );
        dest.writeDouble( longitude );
    }
}
