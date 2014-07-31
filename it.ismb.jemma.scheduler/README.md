# Jemma local scheduler

## Acknowledgement

This code is written by Michele Di Nocera during a Telecom Italia stage on the Energy@Home project, under the supervision of Fabio Bellifemmine, for the Tesl@ specialistic course (Polito). 
All the work was done among the ISMB (Istituto Superiore Mario Boella), under the supervision of Riccardo Tommasi and Ivan Grimaldi.
The scheduler use the fullcalendar javascript plug-in as base (released under MIT license). All the documentation about this plug-in is available at the URL

http://arshaw.com/fullcalendar/

## Why a scheduler?

The scheduler was born as an application of the various control clusters that are included in Jemma and it was developed in a short period of time, for this reason there are some bugs and there are some features that have to be enhanced/implemented.

## How to start the local scheduler 

Download the it.ismb.jemma.scheduler folder, open eclipse and put it in your workspace with the rest of jemma, then run all.

Open the web console (or the web GUI) and install all the appliances you need to schedule.

Open the browser and go to the URL (remember that the local port may change, 8080 as default).

    http://localhost:8080/fullcalendar/webScheduler/Scheduler.html

for the local scheduler, or go to the URL 

    http://localhost:8080/fullcalendar/webScheduler/SchedulerDemo.html

for the scheduler demo.

- The scheduler is a total client-side local scheduler, in fact if you refresh the page you lose all the scheduled events and have to be refreshed every day.

- The scheduler demo have a pretty similar interface to the real scheduler, it have two more buttons for the demo start and stop. You can put the events on the day scheduler and when you click on start button a red line appears, simulating the time flow in a day, allowing the appliances to start and stop when the red line intercepts the events.
Furthermore if there are two or more appliance turned on at the same time the scheduler force an overload warning to the appliances that support this kind of cluster method.