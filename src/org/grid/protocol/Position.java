/**
 *  Position can either set or get current coordinates (x,y).
 *  Position can be scaled for a factor (int) factor.
 *  Position can be printed out with toString().
 *  Check if two Positions are equal with equals.
 *  Position can return it's hash code with hashCode().
 *  A distance can be calculated between two positions.
 *  A Position can be offset by another position with offset(Position p).
 */
package org.grid.protocol;

import java.io.Serializable;


public class Position implements Serializable {
	
	private static final long serialVersionUID = 1L;

	private int x;
	private int y;

    /**
     * Create position from int x and int y
     * @param x
     * @param y
     */
	public Position(int x, int y) {
		super();
		this.x = x;
		this.y = y;
	}

    /**
     * Create position from Position p
     * @param p
     */
	public Position(Position p) {
		super();
		this.x = p.x;
		this.y = p.y;
	}

    /**
     * Position scaled for factor factor
     */
	public Position(Position p, int factor) {
		super();
		this.x = p.x * factor;
		this.y = p.y * factor;
	}
	
	public String toString() {
		return String.format("Position: %d, %d", getX(), getY());
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj != null && obj instanceof Position) 
			return (((Position) obj).x == x && ((Position) obj).y == y);
		else return false;
	}

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31*hash + x;
        hash = 31*hash + y;
        return hash;
    }

    /**
     * Calculate Manhattan distance between two positions.
     * @param p1
     * @param p2
     * @return int distance
     */
    public static int distance(Position p1, Position p2) {
		return Math.max(Math.abs(p1.getX() - p2.getX()), Math.abs(p1.getY()
				- p2.getY()));
    }

    /**
     * Offset position for position points x and y
     */
    public void offset(Position p) {
    	x += p.x;
    	y += p.y;
    }

    // Getters
    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }
    // Setters
    public void setX(int x) {
        this.x = x;
    }
    public void setY(int y) {
        this.y = y;
    }
}
