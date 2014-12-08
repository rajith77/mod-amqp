/*
* Copyright 2011-2012 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
var eb = require("vertx/event_bus");
var console = require("vertx/console");

var msg1 = {'application-properties' : {'routing-key' : 'foo.bar'}, 'body' : 'hello world from foo bar'};
eb.publish('vertx.mod-amqp', msg1);
console.log('Publiser verticle sent msg : ' + JSON.stringify(msg1));


var msg2 = {'application-properties' : {'routing-key' : 'foo.baz'}, 'body' : 'hello world from foo baz'};
eb.publish('vertx.mod-amqp', msg2);
console.log('Publiser verticle sent msg : ' + JSON.stringify(msg2));
