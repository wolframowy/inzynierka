package inz.util;

import java.io.Serializable;

/**
 * Created by Kuba on 02/05/2017.
 * Simple tuple
 */

public class Tuple<X, Y> implements Serializable{
    private X x;
    private Y y;
    public Tuple(X x, Y y) {
        this.x = x;
        this.y = y;
    }

    public void setX(X x) {
        this.x = x;
    }
    public void setY(Y y) {
        this.y = y;
    }

    public X getX() {
        return x;
    }

    public Y getY() {
        return y;
    }
}