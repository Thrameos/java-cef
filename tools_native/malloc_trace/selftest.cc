#include <cstdlib>

void DoFirstFree(void* p) { free(p); }
void DoSecondFree(void* p) { free(p); }

int main() {
  void* p = malloc(64);
  DoFirstFree(p);
  DoSecondFree(p);  // double free -- should be caught
  return 0;
}
