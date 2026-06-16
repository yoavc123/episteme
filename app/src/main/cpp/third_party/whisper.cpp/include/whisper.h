#pragma once

#ifdef __cplusplus
extern "C" {
#endif

struct whisper_context;

struct whisper_context * whisper_init_from_file(const char * path_model);
void whisper_free(struct whisper_context * ctx);

#ifdef __cplusplus
}
#endif
