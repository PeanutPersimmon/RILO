# Add narrowly scoped keep rules only when a concrete dependency requires them.
# Do not keep whole packages by default.

# WorkManager recreates this merger by class name during Glance widget updates.
# Its library rule keeps the class but R8 otherwise removes the reflective constructor.
-keepclassmembers class androidx.work.OverwritingInputMerger {
    public <init>();
}

# Glance instantiates these click callbacks by their retained class names.
# Its library rule keeps the classes; R8 otherwise removes their unused constructors.
-keepclassmembers class io.github.peanutpersimmon.rilo.widget.sleep.SleepCheckInAction {
    public <init>();
}
-keepclassmembers class io.github.peanutpersimmon.rilo.widget.sleep.WakeCheckInAction {
    public <init>();
}

