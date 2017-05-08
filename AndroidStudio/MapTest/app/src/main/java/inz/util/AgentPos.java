package inz.util;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Created by Kuba on 02/05/2017.
 * Simple tuple
 */

public class AgentPos implements Parcelable, Serializable{
    private String name;
    private ParcelableLatLng ll;
    public AgentPos(String name, ParcelableLatLng ll) {
        this.name = name;
        this.ll = ll;
    }

    protected AgentPos(Parcel in) {
        name = in.readString();
        ll = in.readParcelable(ParcelableLatLng.class.getClassLoader());
    }

    public static final Creator<AgentPos> CREATOR = new Creator<AgentPos>() {
        @Override
        public AgentPos createFromParcel(Parcel in) {
            return new AgentPos(in);
        }

        @Override
        public AgentPos[] newArray(int size) {
            return new AgentPos[size];
        }
    };

    public void setName(String name) {
        this.name = name;
    }
    public void setLatLng(ParcelableLatLng ll) {
        this.ll = ll;
    }

    public String getName() {
        return name;
    }

    public ParcelableLatLng getLatLng() {
        return ll;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(ll, flags );
        dest.writeString( name );


    }
}