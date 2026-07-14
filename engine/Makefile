CXX = g++
CXXFLAGS = -std=c++17 -Wall -Wextra -O2

SRCS = src/main.cpp src/elo.cpp src/glicko2.cpp src/poisson.cpp src/plackett_luce.cpp src/bayesian.cpp src/monte_carlo.cpp
OBJS = $(SRCS:.cpp=.o)
TARGET = engine.exe
LDFLAGS = -lpthread

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CXX) $(CXXFLAGS) -o $(TARGET) $(OBJS) $(LDFLAGS)

%.o: %.cpp
	$(CXX) $(CXXFLAGS) -c $< -o $@

clean:
	del /Q src\*.o *.exe 2>nul || exit 0

test: all
	./$(TARGET)
