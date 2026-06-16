#include "whisper.h"

#include <cstdio>

struct whisper_context {
    int placeholder;
};

whisper_context * whisper_init_from_file(const char * path_model) {
    if (path_model == nullptr) return nullptr;
    FILE * file = std::fopen(path_model, "rb");
    if (file == nullptr) return nullptr;
    std::fclose(file);
    return new whisper_context{1};
}

void whisper_free(whisper_context * ctx) {
    delete ctx;
}
