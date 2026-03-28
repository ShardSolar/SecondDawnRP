package net.shard.seconddawnrp.cc;

/**
 * Marker base class for SecondDawnRP ComputerCraft peripherals.
 *
 * All method exposure is handled by @LuaFunction annotations on concrete
 * subclasses — CC discovers them automatically via reflection. No abstract
 * methods needed here.
 *
 * Concrete classes must still implement IPeripheral for getType() and equals().
 */
public abstract class SecondDawnPeripheral {
    // Intentionally empty — @LuaFunction on subclass methods handles everything.
}