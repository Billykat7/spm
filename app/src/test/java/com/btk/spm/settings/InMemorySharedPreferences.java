package com.btk.spm.settings;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link SharedPreferences} held in a map, so {@link AppPreferences} runs on the JVM. Reads cast the
 * stored value the way Android's implementation does, so a value of the wrong type throws
 * {@link ClassCastException} here too. Change listeners are called for each key a commit touches, as
 * Android calls them, but held strongly; Android holds them weakly, which is why the code under test
 * keeps its own reference.
 */
final class InMemorySharedPreferences implements SharedPreferences {

    private final Map<String, Object> values = new HashMap<>();
    private final Set<OnSharedPreferenceChangeListener> listeners = new LinkedHashSet<>();

    @Override
    public Map<String, ?> getAll() {
        return new HashMap<>(values);
    }

    @Override
    public String getString(String key, String defValue) {
        return values.containsKey(key) ? (String) values.get(key) : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        return values.containsKey(key) ? (Set<String>) values.get(key) : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        return values.containsKey(key) ? (Integer) values.get(key) : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        return values.containsKey(key) ? (Long) values.get(key) : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        return values.containsKey(key) ? (Float) values.get(key) : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return values.containsKey(key) ? (Boolean) values.get(key) : defValue;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new MapEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Says how many listeners are registered, so a test can check one is removed when nobody observes.
     *
     * @return the number of registered listeners
     */
    int listenerCount() {
        return listeners.size();
    }

    /** Collects changes and applies them on {@code apply()} or {@code commit()}, as Android does. */
    private final class MapEditor implements Editor {

        private final Map<String, Object> pending = new HashMap<>();
        private final Set<String> removed = new HashSet<>();
        private boolean clear;

        @Override
        public Editor putString(String key, String value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            pending.put(key, values == null ? null : new HashSet<>(values));
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            removed.add(key);
            return this;
        }

        @Override
        public Editor clear() {
            clear = true;
            return this;
        }

        @Override
        public boolean commit() {
            if (clear) {
                values.clear();
            }
            for (String key : removed) {
                values.remove(key);
            }
            values.putAll(pending);
            Set<String> changed = new LinkedHashSet<>(removed);
            changed.addAll(pending.keySet());
            for (OnSharedPreferenceChangeListener listener : new ArrayList<>(listeners)) {
                for (String key : changed) {
                    listener.onSharedPreferenceChanged(InMemorySharedPreferences.this, key);
                }
            }
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
