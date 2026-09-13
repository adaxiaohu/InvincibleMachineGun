package com.codigohasta.addon.utils.alien;

public class AlienTimer {
   private long startTime = -1L;

   public AlienTimer() {
      this.reset();
   }

   public void reset() {
      this.startTime = System.currentTimeMillis();
   }

   public long getMs() {
      return System.currentTimeMillis() - this.startTime;
   }

   public boolean passedS(double s) {
      return this.passed((long)s * 1000L);
   }

   public boolean passedMs(double ms) {
      return this.passed((long)ms);
   }

   public void setMs(long ms) {
      this.startTime = System.currentTimeMillis() - ms;
   }

   public boolean passed(long ms) {
      return System.currentTimeMillis() - this.startTime >= ms;
   }
}
