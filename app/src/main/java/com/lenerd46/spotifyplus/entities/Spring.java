package com.lenerd46.spotifyplus.entities;

import de.robv.android.xposed.XposedBridge;

public class Spring {
    public double velocity;
    public double dampingRatio;
    public double frequency;

    public boolean sleeping;

    public double position;
    public double finalPosition;

    public Spring(double initial, double dampingRatio, double frequency) {
        if(dampingRatio * frequency < 0) {
            throw new IllegalArgumentException("Spring does not converge");
        }

        this.dampingRatio = dampingRatio;
        this.frequency = frequency;
        this.velocity = 0;
        this.position = initial;
        this.finalPosition = initial;
    }

    public double update(double deltaTime) {
        double radialFrequency = (this.frequency * Math.TAU);
        double finalPosition = this.finalPosition;
        double velocity = this.velocity;

        double offset = (this.position - finalPosition);
        double dampingRatio = this.dampingRatio;
        double decay = Math.exp(-dampingRatio * radialFrequency * deltaTime);

        double newPosition;
        double newVelocity;

        if (this.dampingRatio == 1)
        {
            newPosition = (((offset * (1 + radialFrequency * deltaTime) + velocity * deltaTime) * decay) + finalPosition);
            newVelocity = ((velocity * (1 - radialFrequency * deltaTime) - offset * (radialFrequency * radialFrequency * deltaTime)) * decay);
        }
        else if (this.dampingRatio < 1)
        {
            double c = Math.sqrt(1 - (dampingRatio * dampingRatio));

            double i = Math.cos(radialFrequency * c * deltaTime);
            double j = Math.sin(radialFrequency * c * deltaTime);

            double z;
            if (c > 1e-4)
                z = j / c;
            else
            {
                double a = (deltaTime * radialFrequency);
                z = (a + ((((a * a) * (c * c) * (c * c) / 20 - c * c) * (a * a * a)) / 6));
            }

            double y;
            if ((radialFrequency * c) > 1e-4)
                y = (j / (radialFrequency * c));
            else
            {
                double b = (radialFrequency * c);
                y = (deltaTime + ((((deltaTime * deltaTime) * (b * b) * (b * b) / 20 - b * b) * (deltaTime * deltaTime * deltaTime)) / 6));
            }

            newPosition = (((offset * (i + dampingRatio * z) + velocity * y) * decay) + finalPosition);
            newVelocity = ((velocity * (i - z * dampingRatio) - offset * (z * radialFrequency)) * decay);
        }
        else
        {
            double c = Math.sqrt((dampingRatio * dampingRatio) - 1);

            double r1 = (-radialFrequency * (dampingRatio - c));
            double r2 = (-radialFrequency * (dampingRatio + c));

            double co2 = ((velocity - offset * r1) / (2 * radialFrequency * c));
            double co1 = (offset - co2);

            double e1 = (co1 * Math.exp(r1 * deltaTime));
            double e2 = (co2 * Math.exp(r2 * deltaTime));

            newPosition = (e1 + e2 + finalPosition);
            newVelocity = ((e1 * r1) + (e2 * r2));
        }

        this.position = newPosition;
        this.velocity = newVelocity;

        this.sleeping = (Math.abs(finalPosition - newPosition) <= 0.3d);

        return newPosition;
    }

    public void set(double value) {
        this.position = value;
        this.finalPosition = value;
        this.velocity = 0;

        this.sleeping = true;
    }

    public void setFrequency(double value) {
        if(this.dampingRatio * value < 0) {
            throw new IllegalArgumentException("Spring does not converge");
        }

        this.frequency = value;
    }

    public void setDampingRatio(double value) {
        if(value * this.frequency < 0) {
            throw new IllegalArgumentException("Spring does not converge");
        }

        this.dampingRatio = value;
    }
}
