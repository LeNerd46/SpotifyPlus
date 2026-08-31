/*
* LegacySpring.ts from beautiful-lyrics by surfbryce
* https://github.com/surfbryce/beautiful-lyrics/blob/main/Universal/Modules/LegacySpring.ts
*/

const Epsilon = 1e-4
const Pi = Math.PI
const Tau = (Pi * 2)
const Exp = Math.exp
const Sin = Math.sin
const Cos = Math.cos
const Sqrt = Math.sqrt

const SleepEpsilon = 0.1;

export type WorkletSpringState = {
    position: number;
    velocity: number;
    final: number;
    sleeping: boolean;
};

export const createWorkletSpring = (initial: number): WorkletSpringState => ({
    position: initial,
    velocity: 0,
    final: initial,
    sleeping: true,
});

export const setWorkletSpring = (spring: WorkletSpringState, value: number) => {
    'worklet';
    spring.position = value;
    spring.velocity = 0;
    spring.final = value;
    spring.sleeping = true;
    return value;
};

export const updateWorkletSpring = (spring: WorkletSpringState, final: number, dampingRatio: number, frequency: number, deltaTime: number, keepAwake = false,) => {
    'worklet';
    if (!keepAwake && spring.sleeping && final === spring.final) {
        return spring.position;
    }

    spring.sleeping = false;
    const radialFrequency = frequency * Math.PI * 2;
    const offset = spring.position - final;
    const velocity = spring.velocity;
    const decay = Math.exp(-dampingRatio * radialFrequency * deltaTime);

    let newPosition: number;
    let newVelocity: number;

    if (dampingRatio === 1) {
        newPosition = ((offset * (1 + radialFrequency * deltaTime) + velocity * deltaTime) * decay) + final;
        newVelocity = (velocity * (1 - radialFrequency * deltaTime) - offset * radialFrequency * radialFrequency * deltaTime) * decay;
    } else if (dampingRatio < 1) {
        const c = Math.sqrt(1 - dampingRatio * dampingRatio);
        const i = Math.cos(radialFrequency * c * deltaTime);
        const j = Math.sin(radialFrequency * c * deltaTime);
        let z: number;
        let y: number;

        if (c > Epsilon) {
            z = j / c;
        } else {
            const a = deltaTime * radialFrequency;
            z = a + (((a * a * c * c * c * c / 20 - c * c) * a * a * a) / 6);
        }

        if (radialFrequency * c > Epsilon) {
            y = j / (radialFrequency * c);
        } else {
            const b = radialFrequency * c;
            y = deltaTime + (((deltaTime * deltaTime * b * b * b * b / 20 - b * b) * deltaTime * deltaTime * deltaTime) / 6);
        }

        newPosition = ((offset * (i + dampingRatio * z) + velocity * y) * decay) + final; newVelocity = (velocity * (i - z * dampingRatio) - offset * z * radialFrequency) * decay;
    } else {
        const c = Math.sqrt(dampingRatio * dampingRatio - 1);
        const r1 = -radialFrequency * (dampingRatio - c);
        const r2 = -radialFrequency * (dampingRatio + c);
        const co2 = (velocity - offset * r1) / (2 * radialFrequency * c);
        const co1 = offset - co2;
        const e1 = co1 * Math.exp(r1 * deltaTime);
        const e2 = co2 * Math.exp(r2 * deltaTime);

        newPosition = e1 + e2 + final;
        newVelocity = e1 * r1 + e2 * r2;
    }

    spring.position = newPosition;
    spring.velocity = newVelocity;
    spring.final = final;
    spring.sleeping = Math.abs(final - newPosition) <= SleepEpsilon;
    return newPosition;
};

class Spring {
    private Velocity: number
    private DampingRatio: number
    private Frequency: number

    private Sleeping: boolean = true

    public Position: number
    public Final: number

    public constructor(initial: number, dampingRatio: number, frequency: number) {
        if ((dampingRatio * frequency) < 0) {
            throw new Error("Spring does not converge.")
        }

        this.DampingRatio = dampingRatio, this.Frequency = frequency
        this.Velocity = 0
        this.Position = initial, this.Final = initial
    }

    public Update(deltaTime: number): number {
        const radialFrequency = (this.Frequency * Tau)
        const final = this.Final
        const velocity = this.Velocity

        const offset = (this.Position - final)
        const dampingRatio = this.DampingRatio
        const decay = Exp(-dampingRatio * radialFrequency * deltaTime)

        let newPosition, newVelocity

        if (this.DampingRatio == 1) {
            newPosition = (((offset * (1 + radialFrequency * deltaTime) + velocity * deltaTime) * decay) + final)
            newVelocity = ((velocity * (1 - radialFrequency * deltaTime) - offset * (radialFrequency * radialFrequency * deltaTime)) * decay)
        } else if (this.DampingRatio < 1) {
            const c = Sqrt(1 - (dampingRatio * dampingRatio))

            const i = Cos(radialFrequency * c * deltaTime)
            const j = Sin(radialFrequency * c * deltaTime)

            let z
            if (c > Epsilon) {
                z = j / c
            } else {
                const a = (deltaTime * radialFrequency)
                z = (a + ((((a * a) * (c * c) * (c * c) / 20 - c * c) * (a * a * a)) / 6))
            }

            let y
            if ((radialFrequency * c) > Epsilon) {
                y = (j / (radialFrequency * c))
            } else {
                const b = (radialFrequency * c)
                y = (deltaTime + ((((deltaTime * deltaTime) * (b * b) * (b * b) / 20 - b * b) * (deltaTime * deltaTime * deltaTime)) / 6))
            }

            newPosition = (((offset * (i + dampingRatio * z) + velocity * y) * decay) + final)
            newVelocity = ((velocity * (i - z * dampingRatio) - offset * (z * radialFrequency)) * decay)
        } else {
            const c = Sqrt((dampingRatio * dampingRatio) - 1)

            const r1 = (-radialFrequency * (dampingRatio - c))
            const r2 = (-radialFrequency * (dampingRatio + c))

            const co2 = ((velocity - offset * r1) / (2 * radialFrequency * c))
            const co1 = (offset - co2)

            const e1 = (co1 * Exp(r1 * deltaTime))
            const e2 = (co2 * Exp(r2 * deltaTime))

            newPosition = (e1 + e2 + final)
            newVelocity = ((e1 * r1) + (e2 * r2))
        }

        this.Position = newPosition
        this.Velocity = newVelocity

        this.Sleeping = (Math.abs(final - newPosition) <= SleepEpsilon)

        return newPosition
    }

    public Set(value: number) {
        this.Position = value, this.Final = value
        this.Velocity = 0
        this.Sleeping = true
    }

    public SetFrequency(value: number) {
        if ((this.DampingRatio * value) < 0) {
            throw new Error("Spring does not converge.")
        }

        this.Frequency = value
    }

    public SetDampingRatio(value: number) {
        if ((value * this.Frequency) < 0) {
            throw new Error("Spring does not converge.")
        }

        this.DampingRatio = value
    }

    public IsSleeping(): boolean {
        return this.Sleeping
    }
}

export default Spring
