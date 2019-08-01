# Documentation

## Startup

I have not changed how the project is started and can be run by:

`./docker-start.sh`

## Assumptions

### One service
I assume there is only one service paying the invoices. 
Therefore I am not handling any locks of the table in the SQL server.

#### Future work
If we were to process massive amounts of invoices.
Then a single service handling all the requests does not seem optimal. Instead I would
create a job that generates messages per invoice to pay and send them to a queue.
Depending on the infrastructure either use AWS Lambdas to take off the messages and 
pay them. If it is in a hosted environment create a small service instead of the lambda. 
This way we can better handle the horizontal scaling of the jobs. Since we are 
only billing once a month we only need to do fire and forget jobs.

### Scheduler
The scheduler is build into the REST API for convenience. 

#### Future work
Normally I would split API and Scheduler into separate services. 
Scheduling is then not interfering with our API and vice versa. Split the actual 
job code from the scheduling and put a message bus in between to offload the work.

I have just used a simple built-in scheduler of kotlin. I expect the java/kotlin ecosystem
to have more advanced libraries for this. But it seems a bit overkill here.

### Database
Right now I am just using the SQLite database that was included in the app. This is not the
best database for massive amounts of data entries.

#### Future work
For something like invoice entries I would probably go with an event store. Event store entries
are immutable by nature. So we naturally log every transaction that is done without losing
data. We are able to go back and see if the invoice was declined and when. And we can
handle the failed scenarios more gracefully when we have the whole log in the DB.

## External libraries
### [Coroutines][1]
I have only added one library and that is coroutines. I wanted the payments to be done
asynchronous because we are dealing with an external call to the payment provider.
Coroutines seem to be the way to do this. I was looking for the alternative to 
async/await in C#.

## Testing
I have done some basic tests where I have mocked out the external dependencies 
in BillingService. I have only applied tests that cover the business logic.
It make sense to cover more parts of the codebase - but it
seems a bit out-of-scope here. 

[1]: https://github.com/kotlin/kotlinx.coroutines/blob/master/README.md#using-in-your-projects