import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;
import java.lang.Thread;

public class Project
{
  // Constants
  private static final int INFINITY = (int) 10E8;
  
  // App settings
  public static final int THREAD_COUNT = 1;
  public static final boolean PRINT = true;
  public static final int NUM_ITERATIONS = 100;
  public static final int TIME_DELAY_MILLIS = 50;
  public static final String INPUT_FILE = "input.txt";
  
  // Thread variables
  public static volatile boolean _exit = false;
  private static volatile int _currentTime;
  public static AtomicInteger _counter;
  private static Object _timeLock = new Object();
  private static Object _threadLock = new Object();
  private static Object _controllerLock = new Object();
  private static Object _startLock = new Object();
  private static int _threadsFinished = 0;
  
  // Dijkstras variables
  private static int[][] _matrix;
  private static int[][] _travelTime;
  private static int _numNodes;
  private static int [] _turnTime;


  public static void main(String [] args) throws FileNotFoundException, InterruptedException
  {
    // Change input to be agrs[0]
    readInput(INPUT_FILE);
    _counter = new AtomicInteger(0);

    if(PRINT)
    {
      printMatrix();
      // printWaitTimes();
    }

    // doDijkstras();
    _exit = false;

    Thread threadController = new Thread(new ThreadController(_timeLock, _startLock, _threadLock, _controllerLock, _counter, THREAD_COUNT));
    Thread timeController = new Thread(new TimeController(_turnTime, _startLock, _timeLock, TIME_DELAY_MILLIS, NUM_ITERATIONS, PRINT));

    timeController.start();
    threadController.start();

    timeController.join();
    threadController.join();

    

  }

  public static Thread[] createThreads() throws InterruptedException
  {

    Thread[] threads = new Thread[THREAD_COUNT];

    for (int i = 0; i < (THREAD_COUNT); i++)
      threads[i] = new Thread(new multiThread(_matrix, _travelTime, _turnTime, _numNodes, _threadLock, _counter));

    return threads;
  }

  public static void startThreads(Thread[] threads)
  {
    for (int i = 0; i < threads.length; i++)
      threads[i].start();
  }

  public static int getCurrentTime() { return _currentTime; }

  public static void updateCurrentTime() { Project._currentTime++; }
  
  public static int get_numNodes() { return _numNodes; }

  // returns 1d int array of length _numNodes
  public static int[] dijkstras(int source)
  {
    // Time to get to each node from source
    int [] time = new int[_numNodes];
    
    boolean [] visited = new boolean[_numNodes];

    Arrays.fill(time, INFINITY);
    Arrays.fill(visited, false);

    time[source] = 0; // Source to source -> no wait

    for (int count = 0; count < _numNodes; count++)
    {

      // Finds the next min time path from current node
      int minTime = INFINITY;
      int min = -1; // index of min node

      for (int n = 0; n < _numNodes; n++)
      {
        if (!visited[n] && time[n] <= minTime)
        {
          minTime = time[n];
          min = n;
        }
      }

      // Process that min node
      visited[min] = true;

      // Update values of adjacent nodes of i
      for (int n = 0; n < _numNodes; n++)
      {
        if (!visited[n] // not yet visited
            && _matrix[min][n] != INFINITY // edge from node to node
            && time[min] != INFINITY // evaluate path to be shorter & ↓
            && time[min] + _matrix[min][n] + futureWaitTime(time[min], min) < time[n])
        {
          time[n] = time[min] + _matrix[min][n] + futureWaitTime(time[min], min); 
        }
      }
    }
    
    return time;
  }

  // Uses application wide current time to figure future wait time
  public static int futureWaitTime(int futureTime, int node)
  {
    return (_turnTime[node] - ((_currentTime + futureTime) % _turnTime[node]));
  }
  
  public static void readInput(String filename) throws FileNotFoundException
  {
    Scanner scan = new Scanner(new File(filename));

    _numNodes = scan.nextInt();
    
    _matrix = new int[_numNodes][_numNodes]; // matrix that holds the distance between nodes
    _travelTime = new int[_numNodes][_numNodes];
    _turnTime = new int[_numNodes];
    
    
    for (int i = 0; i < _numNodes; i++)
    {
      for (int j = 0; j < _numNodes; j++)
      {
        _matrix[i][j] = scan.nextInt();
        
        if(_matrix[i][j] == -1)
          _matrix[i][j] = INFINITY;
      }
    }

    for (int i = 0; i < _numNodes; i++)
    {
      _turnTime[i] = scan.nextInt();
    }
  }

  public static void printMatrix()
  {
    System.out.println("Matrix:");
    for (int i = 0; i < _numNodes; i++)
    {
      for (int j = 0; j < _numNodes; j++)
      {
        if(_matrix[i][j] == INFINITY)
          System.out.printf("%4c", '*');
        else
          System.out.printf("%4d", _matrix[i][j]);
      }
      System.out.println();
    }
    System.out.println();
  }

  public static void printTravelTimes()
  {
    System.out.println("Travel times:");
    for (int i = 0; i < _numNodes; i++)
    {
      for (int j = 0; j < _numNodes; j++)
      {
        if(_travelTime[i][j] == INFINITY)
          System.out.printf("%4c", '*');
        else
          System.out.printf("%4d", _travelTime[i][j]);
      }
      System.out.println();
    }
    System.out.println();
  }
  
  public static void printWaitTimes()
  {
    System.out.println("Wait times:");
    for(int i = 0; i < _numNodes; i++)
    {
      System.out.printf("%4d",_turnTime[i]);
    }
    System.out.println("\n"); 
  }

  public static void doDijkstras()
  {
    for(int i = 0; i < _numNodes; i++)
    {
      _travelTime[i] = dijkstras(i);
    }
  }

  // Notifies thread controller class when all the threads are finished
  public static synchronized void threadFinished()
  {
    _threadsFinished++;

    if(_threadsFinished == (THREAD_COUNT))
    {
      _threadsFinished = 0;
      synchronized(_controllerLock)
      {
        _controllerLock.notifyAll();
      }
    }
  }
}

// Handles the changing wait times inbetween nodes
class TimeController implements Runnable
{
  int[] _turnTimeMaster; // Holds original turn times (shared across all threads)
  int[] _turnTime; // Local copy of original turn times to update as time goes on
  Object _timeLock;
  Object _startLock;
  
  final int DELAY;
  final int NUM_ITERATIONS;
  final boolean PRINT;


  public TimeController(int [] turnTime, Object startLock, Object timeLock, int delay, int numIterations, boolean print)
  {
    _turnTimeMaster = turnTime;
    _timeLock = timeLock;
    _startLock = startLock;

    this.DELAY = delay;
    this.NUM_ITERATIONS = numIterations;
    this.PRINT = print;

    // Needs a new copy of turntime to not alter original when printing current wait times
    _turnTime = new int[turnTime.length];
    for (int i = 0; i < turnTime.length; i++)
    {
      _turnTime[i] = turnTime[i];
    }
  }

  @Override
  public void run()   
  {
    
    synchronized(_startLock)
    {
      try {
        _startLock.wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    // Must happen to avoid dual printing of same time (not sure exactly why though)
    synchronized(_timeLock)
    {
      _timeLock.notifyAll();
    }


    while(!Project._exit)
    {
      Project.updateCurrentTime();
      
      if(PRINT)
      {
        // -1 required to match up times correctly
        System.out.println("Current time: " + (Project.getCurrentTime() - 1));
        Project.printTravelTimes();
        printCurrentTimes();
        updateTimes();
        System.out.println();
      }
      
      
      try {
        Thread.sleep(DELAY);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      synchronized(_timeLock)
      {
        _timeLock.notifyAll();
      }
    }

    Project.updateCurrentTime();
      
    if(PRINT)
    {
      // -1 required to match up times correctly
      System.out.println("Current time: " + (Project.getCurrentTime() - 1));
      Project.printTravelTimes();
      printCurrentTimes();
      updateTimes();
      System.out.println();
    }
  }

  public void updateTimes()
  {
    // Update array
    for (int i = 0; i < _turnTime.length; i++)
    {
      _turnTime[i]--; // adjusts the turn time of every node

      if (_turnTime[i] == -1)
      {
        // resets the timers back to their original wait times
        _turnTime[i] = _turnTimeMaster[i];
      }
    }
  }

  public void printCurrentTimes()
  {
    System.out.println("Current wait times per station:");

    for (int i = 0; i < _turnTime.length; i++)
    {
      // prints the wait times for each node after time has passed
      System.out.printf("%4d", _turnTime[i]);
    }
    System.out.print("\n");
  }
}

class ThreadController implements Runnable
{
  Object _timeLock;
  Object _startLock;
  Object _threadLock;
  Object _controllerLock;
  AtomicInteger _counter;
  int _threadCount;
  // 0 -> multi thread
  // 1 -> single thread

  Thread[] _threads;

  public ThreadController(Object timeLock, Object startLock, Object threadLock, Object controllerLock, AtomicInteger counter, int threadCount) {
    this._timeLock = timeLock;
    this._startLock = startLock;
    this._threadLock = threadLock;
    this._controllerLock = controllerLock;
    this._counter = counter;
    this._threadCount = threadCount;
  }

  @Override
  public void run()
  {
    float avgTime;
    if(_threadCount == 1)
      avgTime = singleThread();
    else
      avgTime = multiThread();

    if(Project.PRINT)
      try {
        Thread.sleep(150);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    System.out.printf("Average execution time: %4.2f millis", avgTime);
  }

  private float singleThread()
  {
    
    int NUM_ITERATIONS = Project.NUM_ITERATIONS;

    long totalTime = 0;
    long startTime;
    long endTime;

    for(int i = 0; i < NUM_ITERATIONS; i++)
    {
      if(i == (NUM_ITERATIONS - 1))
        Project._exit = true;

      startTime = System.currentTimeMillis();

      Project.doDijkstras();
  
      endTime = System.currentTimeMillis();
  
      totalTime+= (endTime - startTime);

      if(i == 0)
      {
        synchronized(_startLock)
        {
          _startLock.notifyAll();
        }
      }

      if(!Project._exit)
      {
        // Wait for time to increment
        synchronized(_timeLock)
        {
          try {
            _timeLock.wait();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }

    return ((float) totalTime / (float) NUM_ITERATIONS);
  }

  private float multiThread()
  {
    int NUM_ITERATIONS = Project.NUM_ITERATIONS;
    
    try {
      _threads = Project.createThreads();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    startThreads();

    long totalTime = 0;

    for(int i = 0; i < NUM_ITERATIONS; i++)
    {
      if(i == (NUM_ITERATIONS - 1))
        Project._exit = true;

      totalTime += timeDijkstras();

      if(i == 0)
      {
        synchronized(_startLock)
        {
          _startLock.notifyAll();
        }
      }

      if(!Project._exit)
      {
        // Wait for time to increment
        synchronized(_timeLock)
        {
          try {
            _timeLock.wait();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }

    return ((float) totalTime / (float) NUM_ITERATIONS);
  }

  private void startThreads()
  {
    for(int i = 0; i < _threads.length; i++)
    {
      _threads[i].start();
    }
    
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public long timeDijkstras()
  {
    _counter.set(0);
    
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    
    long startTime = System.currentTimeMillis();

    // Do dijkstras in here
    synchronized(_threadLock)
    {
      _threadLock.notifyAll();
    }

    // Wait for threads to finish
    synchronized(_controllerLock)
    {
      try {
        _controllerLock.wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    long endTime = System.currentTimeMillis();

    return (endTime - startTime);
  }
  
}

class multiThread extends Thread
{
  int[][] _matrix; // Adjancency matrix
  int[][] _times; // 
  int[] _turnTime;
  int _numNodes;

  AtomicInteger _counter;
  Object _threadLock;
  private final int INFINITY = (int) 10E8;

  public multiThread(int matrix[][], int times[][], int[] turnTime, int numNodes, Object threadLock, AtomicInteger counter)
  {
    this._matrix = matrix;
    this._times = times;
    this._turnTime = turnTime;
    this._numNodes = numNodes;
    this._threadLock = threadLock;
    this._counter = counter;
  }
  
  // returns 1d int array of length _numNodes
  public int[] dijkstras(int source)
  {
    if (source >= _numNodes)
      System.err.println("Dijsktras input out of bounds, too high.");

    // Time to get to each node from source
    int [] time = new int[_numNodes];
    
    boolean [] visited = new boolean[_numNodes];

    Arrays.fill(time, INFINITY);
    Arrays.fill(visited, false);

    time[source] = 0; // Source to source -> no wait

    for (int i = 0; i < _numNodes; i++)
    {

      // Finds the next min time path from current node
      int minTime = INFINITY;
      int min = -1; // index of min node

      for (int n = 0; n < _numNodes; n++)
      {
        if (!visited[n] && time[n] <= minTime)
        {
          minTime = time[n];
          min = n;
        }
      }

      // Process that min node
      visited[min] = true;
      int waitTime;

      // Update values of adjacent nodes of i
      for (int n = 0; n < _numNodes; n++)
      {
        if (!visited[n] // not yet visited
            && _matrix[min][n] != INFINITY // edge from node to node
            && time[min] != INFINITY // evaluate path to be shorter & ↓
            && time[min] + _matrix[min][n] + (waitTime = (_turnTime[min] - ((Project.getCurrentTime() + time[min]) % _turnTime[min]))) < time[n])
        {
          time[n] = time[min] + _matrix[min][n] + waitTime; 
        }
      }
    }
    
    return time;
  }

  @Override
  public void run()   // what the thread will do
  {
    int source;
    
    while(!Project._exit)
    {
      synchronized(_threadLock)
      {
        try {
          _threadLock.wait();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }

      // Take source number from pool while its less than the num nodes
      while((source = _counter.getAndIncrement()) < _numNodes)
      {
        // run dijkstras on that source and store it in times
        _times[source] = dijkstras(source);
      }

      Project.threadFinished();
    }
  }
}
