#include <stdio.h>

void algoritm1() {
	printf("Start algorithm 1\n");
	for (int i = 0; i < 10; i++) {
		printf("Generate num: %d\n", i);
	}

	printf("End algorithm 1\n");
}

int main() {
    printf("Running Generic App\n");
    
    algoritm1();

    return 0;
}

