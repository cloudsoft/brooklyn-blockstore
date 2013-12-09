brooklyn-blockstore
===================

This provides an abstract for cloud blockstores (i.e. volumes), providing a common API for EC2, OpenStack, etc.

It provides operations for creating, attaching, mounting, unmounting, detaching and deleting volumes. It also includes the required filesystem operations for this.

To build the project, run:

`mvn clean install -DskipTests`


Watch this space
----------------

The code currently available is just the first step. Future features include:

* Fix the profiles to exclude live tests from the normal build.
* Support for more clouds, including Google Compute Engine and CloudStack.


Checkout Brooklyn
-----------------

This project builds on the open-source project Brooklyn (see http://brooklyncentral.github.io and https://github.com/brooklyncentral/brooklyn).

================

&copy; 2013 Cloudsoft Corporation Limited. All rights reserved.

Use of this software is subject to the Cloudsoft EULA, provided in LICENSE.md and at 

http://www.cloudsoftcorp.com/cloudsoft-developer-license

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
