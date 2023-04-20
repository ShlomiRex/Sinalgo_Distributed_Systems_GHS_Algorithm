# Sinalgo - GHS algorithm with client/server application

## What is this
Matala 15 in Distrubted Systems at the Open University of Israel

In this assignment, the students (me) need to use the GHS algorithm which consists of 7 phases.

In addition, after the GHS finishes, and we have MST (Minimum Spanning Tree), we also want to send a message
from one node to another. The client node is the sender, and the server is the receiver.

Before the algorithm starts, at round = 0, the user selects which node should be become the server.

The selected node will become the new MST leader. Note: without selecting a server, some node, call it X, becomes the MST leader. But, when user selects node Y to become server, after GHS finishes and node X becomes MST leader, we run another phase: phase 9. In this phase, the original MST leader (node X) switches places with the server (node Y), so that node Y becomes the new MST leader.

After node Y becomes the MST leader, the algorithm finishes, and user can then start sending any message from any node to the MST leader (node Y).

## Videos explaining the submission

1. Explains my algorithm creates MST: https://www.youtube.com/watch?v=_5p9-792u5g
2. Server & Client implementation: https://www.youtube.com/watch?v=PrCY5Sg487c
3. Explaining all the code: https://www.youtube.com/watch?v=VQf7QPvxaiE

## Submission

The files submitted (which I got a grade of 100 on this assignment) are located in a folder called 'submission'.