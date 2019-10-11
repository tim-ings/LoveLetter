from pprint import pprint
import subprocess
import sys


def main():
    test_count = 10 * 100
    if len(sys.argv) >= 2:
        test_count = int(sys.argv[1])
    results = [0] * 4
    for x in range(test_count):
        res = subprocess.check_output(['C:/Program Files/Java/jdk-12.0.2/bin/java.exe', '-jar', 'loveletter.jar'])
        res_parts = str(res).split('\\n')
        #pprint(res_parts)
        winner = res_parts[-7][9]
        winner = int(winner)
        results[winner] += 1
    i = 0
    for res in results:
        print("Result: Player {} won {} times.".format(i, res))
        i += 1


if __name__ == "__main__":
    main()
