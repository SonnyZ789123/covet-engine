/*
 * Native peer for java.lang.StrictMath. jpf-core ships peers for java.lang.Math
 * only, so SUTs that call StrictMath (e.g. Apache Commons Math's FastMath
 * initializer) fail with UnsatisfiedLinkError during class load.
 *
 * This peer mirrors the Math peer's API, delegating to the host
 * java.lang.StrictMath. No symbolic wiring - StrictMath is typically used in
 * precomputed constants, off the concolic path.
 */
package gov.nasa.jpf.jdart.peers;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.NativePeer;

public class JPF_java_lang_StrictMath extends NativePeer {

  @MJI public double abs__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.abs(a); }
  @MJI public float  abs__F__F(MJIEnv env, int clsRef, float a)  { return StrictMath.abs(a); }
  @MJI public int    abs__I__I(MJIEnv env, int clsRef, int a)    { return StrictMath.abs(a); }
  @MJI public long   abs__J__J(MJIEnv env, int clsRef, long a)   { return StrictMath.abs(a); }

  @MJI public double max__DD__D(MJIEnv env, int clsRef, double a, double b) { return StrictMath.max(a, b); }
  @MJI public float  max__FF__F(MJIEnv env, int clsRef, float a, float b)   { return StrictMath.max(a, b); }
  @MJI public int    max__II__I(MJIEnv env, int clsRef, int a, int b)       { return StrictMath.max(a, b); }
  @MJI public long   max__JJ__J(MJIEnv env, int clsRef, long a, long b)     { return StrictMath.max(a, b); }

  @MJI public double min__DD__D(MJIEnv env, int clsRef, double a, double b) { return StrictMath.min(a, b); }
  @MJI public float  min__FF__F(MJIEnv env, int clsRef, float a, float b)   { return StrictMath.min(a, b); }
  @MJI public int    min__II__I(MJIEnv env, int clsRef, int a, int b)       { return StrictMath.min(a, b); }
  @MJI public long   min__JJ__J(MJIEnv env, int clsRef, long a, long b)     { return StrictMath.min(a, b); }

  @MJI public double sqrt__D__D(MJIEnv env, int clsRef, double a)             { return StrictMath.sqrt(a); }
  @MJI public double cbrt__D__D(MJIEnv env, int clsRef, double a)             { return StrictMath.cbrt(a); }
  @MJI public double pow__DD__D(MJIEnv env, int clsRef, double a, double b)   { return StrictMath.pow(a, b); }

  @MJI public double exp__D__D(MJIEnv env, int clsRef, double a)   { return StrictMath.exp(a); }
  @MJI public double expm1__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.expm1(a); }
  @MJI public double log__D__D(MJIEnv env, int clsRef, double a)   { return StrictMath.log(a); }
  @MJI public double log10__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.log10(a); }
  @MJI public double log1p__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.log1p(a); }

  @MJI public double sin__D__D(MJIEnv env, int clsRef, double a)  { return StrictMath.sin(a); }
  @MJI public double cos__D__D(MJIEnv env, int clsRef, double a)  { return StrictMath.cos(a); }
  @MJI public double tan__D__D(MJIEnv env, int clsRef, double a)  { return StrictMath.tan(a); }
  @MJI public double asin__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.asin(a); }
  @MJI public double acos__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.acos(a); }
  @MJI public double atan__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.atan(a); }
  @MJI public double atan2__DD__D(MJIEnv env, int clsRef, double y, double x) { return StrictMath.atan2(y, x); }

  @MJI public double sinh__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.sinh(a); }
  @MJI public double cosh__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.cosh(a); }
  @MJI public double tanh__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.tanh(a); }

  @MJI public double ceil__D__D(MJIEnv env, int clsRef, double a)  { return StrictMath.ceil(a); }
  @MJI public double floor__D__D(MJIEnv env, int clsRef, double a) { return StrictMath.floor(a); }
  @MJI public double rint__D__D(MJIEnv env, int clsRef, double a)  { return StrictMath.rint(a); }

  @MJI public long round__D__J(MJIEnv env, int clsRef, double a) { return StrictMath.round(a); }
  @MJI public int  round__F__I(MJIEnv env, int clsRef, float a)  { return StrictMath.round(a); }

  @MJI public double random____D(MJIEnv env, int clsRef) { return StrictMath.random(); }

  @MJI public double IEEEremainder__DD__D(MJIEnv env, int clsRef, double f1, double f2) { return StrictMath.IEEEremainder(f1, f2); }
  @MJI public double hypot__DD__D(MJIEnv env, int clsRef, double x, double y)             { return StrictMath.hypot(x, y); }

  @MJI public double ulp__D__D(MJIEnv env, int clsRef, double d) { return StrictMath.ulp(d); }
  @MJI public float  ulp__F__F(MJIEnv env, int clsRef, float f)  { return StrictMath.ulp(f); }

  @MJI public double signum__D__D(MJIEnv env, int clsRef, double d) { return StrictMath.signum(d); }
  @MJI public float  signum__F__F(MJIEnv env, int clsRef, float f)  { return StrictMath.signum(f); }

  @MJI public double copySign__DD__D(MJIEnv env, int clsRef, double magnitude, double sign) { return StrictMath.copySign(magnitude, sign); }
  @MJI public float  copySign__FF__F(MJIEnv env, int clsRef, float magnitude, float sign)   { return StrictMath.copySign(magnitude, sign); }

  @MJI public int getExponent__D__I(MJIEnv env, int clsRef, double d) { return StrictMath.getExponent(d); }
  @MJI public int getExponent__F__I(MJIEnv env, int clsRef, float f)  { return StrictMath.getExponent(f); }

  @MJI public double nextAfter__DD__D(MJIEnv env, int clsRef, double start, double direction) { return StrictMath.nextAfter(start, direction); }
  @MJI public float  nextAfter__FD__F(MJIEnv env, int clsRef, float start, double direction)  { return StrictMath.nextAfter(start, direction); }

  @MJI public double nextUp__D__D(MJIEnv env, int clsRef, double d) { return StrictMath.nextUp(d); }
  @MJI public float  nextUp__F__F(MJIEnv env, int clsRef, float f)  { return StrictMath.nextUp(f); }

  @MJI public double scalb__DI__D(MJIEnv env, int clsRef, double d, int scaleFactor) { return StrictMath.scalb(d, scaleFactor); }
  @MJI public float  scalb__FI__F(MJIEnv env, int clsRef, float f, int scaleFactor)  { return StrictMath.scalb(f, scaleFactor); }

  @MJI public double toRadians__D__D(MJIEnv env, int clsRef, double angdeg) { return StrictMath.toRadians(angdeg); }
  @MJI public double toDegrees__D__D(MJIEnv env, int clsRef, double angrad) { return StrictMath.toDegrees(angrad); }
}
