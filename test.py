from pprint import pprint
import subprocess
import sys
import random


def main():
    test_count = 10 * 100
    if len(sys.argv) >= 2:
        test_count = int(sys.argv[1])
    myWins = 0
    for x in range(test_count):
        myIndex = random.randint(0, 3)
        res = subprocess.check_output(['C:/Program Files/Java/jdk-12.0.2/bin/java.exe', '-jar', 'loveletter.jar', str(myIndex)])
        res_parts = str(res).split('\\n')
        winner = res_parts[-7][9]
        winner = int(winner)
        if winner == myIndex:
            myWins += 1
    print("Result: We won {} times ({}% win rate, random rate is 25%).".format(myWins, myWins / test_count * 100))


if __name__ == "__main__":
    main()
