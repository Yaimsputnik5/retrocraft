package com.sammwy.retrocraft.cores.worker;

import com.sun.jna.*;

/** libretro.h uses C99 bool (one byte) and platform-sized size_t. */
public interface Libretro extends Library {
    class SizeT extends IntegerType {
        public SizeT() {
            this(0);
        }

        public SizeT(long value) {
            super(Native.SIZE_T_SIZE, value, true);
        }
    }

    interface Environment extends Callback {
        byte invoke(int command, Pointer data);
    }

    interface Video extends Callback {
        void invoke(Pointer data, int width, int height, SizeT pitch);
    }

    interface Audio extends Callback {
        void invoke(short left, short right);
    }

    interface AudioBatch extends Callback {
        SizeT invoke(Pointer data, SizeT frames);
    }

    interface InputPoll extends Callback {
        void invoke();
    }

    interface InputState extends Callback {
        short invoke(int port, int device, int index, int id);
    }

    /**
     * retro_log_printf_t is variadic; reading only the fixed arguments is ABI-safe on every
     * supported platform.
     */
    interface Log extends Callback {
        void invoke(int level, String format);
    }

    @Structure.FieldOrder({"name", "version", "extensions", "needFullpath", "blockExtract"})
    class SystemInfo extends Structure {
        public String name, version, extensions;
        public byte needFullpath, blockExtract;
    }

    @Structure.FieldOrder({"path", "data", "size", "meta"})
    class GameInfo extends Structure {
        public String path;
        public Pointer data;
        public SizeT size = new SizeT();
        public String meta;
    }

    @Structure.FieldOrder({"baseWidth", "baseHeight", "maxWidth", "maxHeight", "aspectRatio"})
    class Geometry extends Structure {
        public int baseWidth, baseHeight, maxWidth, maxHeight;
        public float aspectRatio;
    }

    @Structure.FieldOrder({"fps", "sampleRate"})
    class Timing extends Structure {
        public double fps, sampleRate;
    }

    @Structure.FieldOrder({"geometry", "timing"})
    class AvInfo extends Structure {
        public Geometry geometry = new Geometry();
        public Timing timing = new Timing();
    }

    int retro_api_version();

    void retro_set_environment(Environment callback);

    void retro_set_video_refresh(Video callback);

    void retro_set_audio_sample(Audio callback);

    void retro_set_audio_sample_batch(AudioBatch callback);

    void retro_set_input_poll(InputPoll callback);

    void retro_set_input_state(InputState callback);

    void retro_set_controller_port_device(int port, int device);

    void retro_init();

    void retro_get_system_info(SystemInfo info);

    void retro_get_system_av_info(AvInfo info);

    byte retro_load_game(GameInfo info);

    void retro_run();

    SizeT retro_serialize_size();

    byte retro_serialize(Pointer data, SizeT size);

    byte retro_unserialize(Pointer data, SizeT size);

    Pointer retro_get_memory_data(int id);

    SizeT retro_get_memory_size(int id);

    void retro_unload_game();

    void retro_deinit();
}
