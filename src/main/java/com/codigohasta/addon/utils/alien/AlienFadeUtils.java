package com.codigohasta.addon.utils.alien;

public class AlienFadeUtils {
   public long length;
   private long start;

   public AlienFadeUtils(long ms) {
      this.length = ms;
      this.reset();
   }

   public void reset() {
      this.start = System.currentTimeMillis();
   }

   public boolean isEnd() {
      return this.getTime() >= this.length;
   }

   protected long getTime() {
      return System.currentTimeMillis() - this.start;
   }

   public void setLength(long length) {
      this.length = length;
   }

   public double getFadeOne() {
      return this.isEnd() ? 1.0 : (double)this.getTime() / this.length;
   }

   public double ease(AlienEasing easing) {
      return easing.ease(this.getFadeOne());
   }
}
