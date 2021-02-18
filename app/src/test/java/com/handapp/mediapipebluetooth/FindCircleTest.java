package com.handapp.mediapipebluetooth;

import java.text.*;
import org.junit.Test;
import static org.junit.Assert.*;
import mikera.vectorz.Vector2;

public class FindCircleTest {
    static DecimalFormat df;
    static double r;
    static int h;
    static int k;

    @Test
    public void main() {
        findCircle(Vector2.of(1, 1), Vector2.of(2, 4), Vector2.of(5, 3));
        assertEquals(2.23607, r, 1);
        assertEquals(3, h, 0);
        assertEquals(2, k, 0);
    }

    static void findCircle(Vector2 point1, Vector2 point2, Vector2 point3)
    {
        // Function to find the circle on
        // which the given three points lie
        int x12 = (int) point1.x - (int) point2.x;
        int x13 = (int) point1.x - (int) point3.x;

        int y12 = (int) point1.y - (int) point2.y;
        int y13 = (int) point1.y - (int) point3.y;

        int y31 = (int) point3.y - (int) point1.y;
        int y21 = (int) point2.y - (int) point1.y;

        int x31 = (int) point3.x - (int) point1.x;
        int x21 = (int) point2.x - (int) point1.x;

        // x1^2 - x3^2
        int sx13 = (int) (Math.pow(point1.x, 2) -
                Math.pow(point3.x, 2));

        // y1^2 - y3^2
        int sy13 = (int) (Math.pow(point1.y, 2) -
                Math.pow(point3.y, 2));

        int sx21 = (int) (Math.pow(point2.x, 2) -
                Math.pow(point1.x, 2));

        int sy21 = (int) (Math.pow(point2.y, 2) -
                Math.pow(point1.y, 2));

        int f = ((sx13) * (x12)
                + (sy13) * (x12)
                + (sx21) * (x13)
                + (sy21) * (x13))
                / (2 * ((y31) * (x12) - (y21) * (x13)));
        int g = ((sx13) * (y12)
                + (sy13) * (y12)
                + (sx21) * (y13)
                + (sy21) * (y13))
                / (2 * ((x31) * (y12) - (x21) * (y13)));

        int c = -(int) Math.pow((int) point1.x, 2) - (int) Math.pow((int) point1.y, 2) -
                2 * g * (int) point1.x - 2 * f * (int) point1.y;

        // eqn of circle be x^2 + y^2 + 2*g*x + 2*f*y + c = 0
        // where centre is (h = -g, k = -f) and radius r
        // as r^2 = h^2 + k^2 - c
        h = -g;
        k = -f;
        int sqr_of_r = h * h + k * k - c;

        // r is the radius
        r = Math.sqrt(sqr_of_r);
        df = new DecimalFormat("#.#####");
        System.out.println("Centre = ("+h +","+k +")");
        System.out.println("Radius = "+df.format(r));
    }
}

// This code is contributed by chandan_jnu
// https://www.geeksforgeeks.org/equation-of-circle-when-three-points-on-the-circle-are-given/