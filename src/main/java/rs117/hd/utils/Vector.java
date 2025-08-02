/*
 * Vector utility functions
 * Written in 2025 by Hooder <ahooder@protonmail.com>
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights
 * to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package rs117.hd.utils;

import java.util.Arrays;

/**
 * Vector utility class similar in style to GLSL, for performing floating-point math operations on raw Java arrays.
 * Usability and conciseness is prioritized, however most methods at least allow avoiding unnecessary allocations.
 * Wherever it makes sense, inputs of different sizes are allowed. Shorter inputs will be repeated to fill the output vector.
 * When automatically determining the output vector length, the minimum length of all inputs is used.
 * Certain floating-point variants are provided also for basic non-vector math operations.
 */
public class Vector {
	public static float[] vec(float... vec) {
		return vec;
	}

	public static float[] vec(int... ivec) {
		float[] floats = zeros(ivec.length);
		for (int i = 0; i < ivec.length; i++)
			floats[i] = ivec[i];
		return floats;
	}

	public static int[] ivec(float... vec) {
		int[] ivec = new int[vec.length];
		for (int i = 0; i < vec.length; i++)
			ivec[i] = (int) vec[i];
		return ivec;
	}

	public static float[] zeros(int n) {
		return new float[n];
	}

	public static float[] zeros(float[]... vectors) {
		assert vectors.length > 0;
		int max = vectors[0].length;
		for (float[] v : vectors)
			max = max(max, v.length);
		return zeros(max);
	}

	public static float[] copy(float[] v) {
		return Arrays.copyOf(v, v.length);
	}

	public static float[] add(float[] out, float[] a, float... b) {
		for (int i = 0; i < out.length; i++)
			out[i] = a[i % out.length] + b[i % b.length];
		return out;
	}

	public static float[] add(float[] a, float[] b) {
		return add(zeros(a), a, b);
	}

	public static float[] subtract(float[] out, float[] a, float... b) {
		for (int i = 0; i < out.length; i++)
			out[i] = a[i % a.length] - b[i % b.length];
		return out;
	}

	public static float[] subtract(float[] a, float[] b) {
		return subtract(zeros(a), a, b);
	}

	public static float[] multiply(float[] out, float[] a, float... b) {
		for (int i = 0; i < out.length; i++)
			out[i] = a[i % a.length] * b[i % b.length];
		return out;
	}

	public static float[] multiply(float[] a, float... b) {
		return multiply(zeros(a), a, b);
	}

	public static float[] divide(float[] out, float[] a, float... b) {
		for (int i = 0; i < out.length; i++)
			out[i] = a[i % a.length] * (b[i % b.length] == 0 ? 0 : 1 / b[i % b.length]);
		return out;
	}

	public static float[] divide(float[] a, float... b) {
		return divide(zeros(a), a, b);
	}

	/**
	 * Modulo which returns the answer with the same sign as the modulus.
	 */
	public static float mod(float v, float mod) {
		return v - floor(v / mod) * mod;
	}

	public static int mod(long v, int mod) {
		return (int) (v - (v / mod) * mod);
	}

	public static float mod(double v, float mod) {
		return (float) (v - Math.floor(v / mod) * mod);
	}

	public static float[] mod(float[] out, float[] v, float... mod) {
		for (int i = 0; i < out.length; i++)
			out[i] = mod(v[i % v.length], mod[i % mod.length]);
		return out;
	}

	public static float[] mod(float[] v, float... mod) {
		return mod(zeros(v), v, mod);
	}

	public static float pow(float base, float exp) {
		return (float) Math.pow(base, exp);
	}

	public static float[] pow(float[] out, float[] base, float... exp) {
		for (int i = 0; i < out.length; i++)
			out[i] = pow(base[i % base.length], exp[i % exp.length]);
		return out;
	}

	public static float[] pow(float[] in, float exp) {
		return pow(zeros(in), in, exp);
	}

	public static float log(float v) {
		return (float) Math.log(v);
	}

	public static float[] log(float[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = log(v[i % v.length]);
		return out;
	}

	public static float[] log(float... v) {
		return log(zeros(v), v);
	}

	public static float log2(float v) {
		return log(v) / log(2);
	}

	public static float[] log2(float[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = log2(v[i % v.length]);
		return out;
	}

	public static float[] log2(float... v) {
		return log2(zeros(v), v);
	}

	public static float sqrt(float v) {
		return (float) Math.sqrt(v);
	}

	public static float[] sqrt(float[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = sqrt(v[i % v.length]);
		return out;
	}

	public static float[] sqrt(float... v) {
		return sqrt(zeros(v), v);
	}

	public static float dot(float[] a, float[] b, int n) {
		float f = 0;
		for (int i = 0; i < n; i++)
			f += a[i] * b[i];
		return f;
	}

	public static float dot(float[] a, float[] b) {
		return dot(a, b, min(a.length, b.length));
	}

	public static float[] cross(float[] out, float[] a, float[] b) {
		out[0] = a[1] * b[2] - a[2] * b[1];
		out[1] = a[2] * b[0] - a[0] * b[2];
		out[2] = a[0] * b[1] - a[1] * b[0];
		return out;
	}

	public static float[] cross(float[] a, float[] b) {
		return cross(zeros(3), a, b);
	}

	public static float length(float... v) {
		return (float) Math.sqrt(dot(v, v));
	}

	public static float distance(float[] a, float[] b, int n) {
		return (float) Math.sqrt(dot(a, a, n) - 2 * dot(a, b, n) + dot(b, b, n));
	}

	public static float distance(float[] a, float[] b) {
		return distance(a, b, min(a.length, b.length));
	}

	public static float[] normalize(float[] out, float... v) {
		return divide(out, v, length(v));
	}

	public static float[] normalize(float... v) {
		return normalize(zeros(v), v);
	}

	public static float abs(float v) {
		return Math.abs(v);
	}

	public static int abs(int v) {
		return Math.abs(v);
	}

	public static float[] abs(float[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = abs(v[i % v.length]);
		return out;
	}

	public static float[] abs(float[] v) {
		return abs(zeros(v), v);
	}

	public static int floor(float v) {
		return (int) Math.floor(v);
	}

	public static int[] floor(int[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = floor(v[i % v.length]);
		return out;
	}

	public static int[] floor(float[] v) {
		return floor(new int[v.length], v);
	}

	public static int ceil(float v) {
		return (int) Math.ceil(v);
	}

	public static int[] ceil(int[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = ceil(v[i % v.length]);
		return out;
	}

	public static int[] ceil(float[] v) {
		return ceil(new int[v.length], v);
	}

	public static int round(float v) {
		return Math.round(v);
	}

	public static int[] round(int[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = round(v[i % v.length]);
		return out;
	}

	public static int[] round(float[] v) {
		return round(new int[v.length], v);
	}

	public static float[] roundf(float[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = round(v[i % v.length]);
		return out;
	}

	public static float[] roundf(float[] v) {
		return roundf(zeros(v), v);
	}

	public static int min(int a, int b) {
		return Math.min(a, b);
	}

	public static int min(int... v) {
		assert v.length > 0;
		int min = v[0];
		for (int i : v)
			min = min(min, i);
		return min;
	}

	public static float min(float a, float b) {
		return Math.min(a, b);
	}

	public static float min(float... v) {
		assert v.length > 0;
		float min = v[0];
		for (float i : v)
			min = min(min, i);
		return min;
	}

	public static float[] min(float[] out, float[] a, float... b) {
		for (int i = 0; i < out.length; i++)
			out[i] = min(a[i % a.length], b[i % b.length]);
		return out;
	}

	public static float[] min(float[] a, float... b) {
		return min(zeros(a, b), a, b);
	}

	public static int max(int a, int b) {
		return Math.max(a, b);
	}

	public static int max(int... v) {
		assert v.length > 0;
		int max = v[0];
		for (int i : v)
			max = max(max, i);
		return max;
	}

	public static float max(float a, float b) {
		return Math.max(a, b);
	}

	public static float max(float... v) {
		assert v.length > 0;
		float max = v[0];
		for (float i : v)
			max = max(max, i);
		return max;
	}

	public static float[] max(float[] out, float[] a, float... b) {
		for (int i = 0; i < out.length; i++)
			out[i] = max(a[i % a.length], b[i % b.length]);
		return out;
	}

	public static float[] max(float[] a, float... b) {
		return max(zeros(a, b), a, b);
	}

	public static float clamp(float v, float min, float max) {
		return min(max(v, min), max);
	}

	public static float clamp(double v, float min, float max) {
		return clamp((float) v, min, max);
	}

	public static int clamp(int v, int min, int max) {
		return min(max(v, min), max);
	}

	public static float[] clamp(float[] out, float[] v, float[] min, float... max) {
		for (int i = 0; i < out.length; i++)
			out[i] = clamp(v[i % v.length], min[i % min.length], max[i % max.length]);
		return out;
	}

	public static float[] clamp(float[] out, float[] v, float min, float... max) {
		return clamp(out, v, vec(min), max);
	}

	public static float[] clamp(float[] v, float[] min, float... max) {
		return clamp(zeros(v), v, min, max);
	}

	public static float[] clamp(float[] v, float min, float... max) {
		return clamp(zeros(v), v, vec(min), max);
	}

	public static float saturate(float v) {
		return clamp(v, 0, 1);
	}

	public static float saturate(double v) {
		return saturate((float) v);
	}

	public static float[] saturate(float[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = clamp(v[i % v.length], 0, 1);
		return out;
	}

	public static float[] saturate(float... v) {
		return saturate(zeros(v), v);
	}

	public static float fract(float v) {
		return mod(v, 1);
	}

	public static float[] fract(float[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = fract(v[i % out.length]);
		return out;
	}

	public static float[] fract(float... v) {
		return fract(zeros(v), v);
	}

	public static float sign(float v) {
		return v < 0 ? -1 : 1;
	}

	public static float[] sign(float[] out, float... v) {
		for (int i = 0; i < out.length; i++)
			out[i] = sign(v[i % out.length]);
		return out;
	}

	public static float[] sign(float... v) {
		return sign(zeros(v), v);
	}

	public static float mix(float v0, float v1, float factor) {
		return v0 * (1 - factor) + v1 * factor;
	}

	public static float[] mix(float[] out, float[] v0, float[] v1, float... factor) {
		for (int i = 0; i < out.length; i++)
			out[i] = mix(v0[i % v0.length], v1[i % v1.length], factor[i % factor.length]);
		return out;
	}

	public static float[] mix(float[] v0, float[] v1, float... factor) {
		return mix(zeros(v0, v1), v0, v1, factor);
	}

	public static float smoothstep(float v0, float v1, float factor) {
		float t = saturate((factor - v0) / (v1 - v0));
		return t * t * (3 - 2 * t);
	}

	public static float[] smoothstep(float[] out, float[] v0, float[] v1, float... factor) {
		for (int i = 0; i < out.length; i++)
			out[i] = smoothstep(v0[i % v0.length], v1[i % v1.length], factor[i % factor.length]);
		return out;
	}

	public static float[] smoothstep(float[] v0, float[] v1, float... factor) {
		return smoothstep(zeros(v0, v1), v0, v1, factor);
	}

	public static float sum(float... v) {
		float sum = 0;
		for (float value : v)
			sum += value;
		return sum;
	}

	public static float avg(float... v) {
		return sum(v) / v.length;
	}

	public static float sin(float rad) {
		return (float) Math.sin(rad);
	}

	public static float cos(float rad) {
		return (float) Math.cos(rad);
	}
}
