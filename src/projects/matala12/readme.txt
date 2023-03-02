How to run
==========
1. Run sample6 from the menu
2. Click on the run button of the simulation (step by step)
3. The first step starts the six_vcol algorithm by broadcasting the colors
4. Then it takes 3 more steps to finish (if the algorithm takes 3 turns)
	Also, notice the console logs. It contains information about the running time of the algorithm.
	Each color 0..5 represented by spesific color to make it obvious.
	Lastly, each edge shows what messages are being sent.
	If there is more than 2 messages on the same edge, the message closer to the node is the sender.
5. At the 5th step, the MIS algorithm begins.
6. After 1..K (where K is the amount of six_vcol steps needed, basically log*n) the MIS algorithm ends.

You can run simulation, then build a new tree, and run it again.
You can also create new tree before running the simulation.

In total the whole algorithm takes log*n (because of six_vcol and MIS algo takes both log*n)