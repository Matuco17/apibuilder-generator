package models

import lib.{Datatype, Primitives, Type, TypeKind}
import com.gilt.apidocgenerator.models.{InvocationForm, Method, Operation, Resource, Service}
import generator.{ScalaOperation, ScalaResource, ScalaService}
import org.scalatest.{ShouldMatchers, FunSpec}

class Play2RouteGeneratorSpec extends FunSpec with ShouldMatchers {

  def getResource(service: Service, name: String): Resource = {
    service.resources.get(name).getOrElse {
      sys.error(s"Could not find $name resource")
    }
  }

  def getScalaResource(ssd: ScalaService, name: String): ScalaResource = {
    ssd.resources.get(name).getOrElse {
      sys.error(s"Could not find $name resource")
    }
  }

  def getScalaMethod(ssd: ScalaService, resourceName: String, method: Method, path: String): ScalaOperation = {
    val resource = getScalaResource(ssd, resourceName)
    resource.operations.filter { op => op.method == method && op.path == path }.headOption.getOrElse {
      val errorMsg = s"Operations found for $resourceName\n" + resource.operations.map { op =>
        "%s %s".format(op.method, op.path)
      }.mkString("\n")
      sys.error(s"Failed to find method[$method] with path[$path] for resource[${resourceName}]\n$errorMsg")
    }
  }

  describe("with apidoc service") {
    lazy val service = TestHelper.parseFile(s"../api/api.json")
    lazy val ssd = new ScalaService(service)

    describe("users resource") {
      lazy val userResource = getScalaResource(ssd, "user")

      it("GET w/ default path, parameters") {
        val op = userResource.operations.filter { op => op.method == Method.Get && op.path == "/users" }.head
        val r = Play2Route(ssd, "user", service.models("user"), op, userResource)
        r.verb should be("GET")
        r.url should be("/users")
        r.method should be("controllers.Users.get")
        r.params.mkString(", ") should be("guid: scala.Option[_root_.java.util.UUID], email: scala.Option[String], token: scala.Option[String]")
      }

      it("GET w/ path, guid path param, no additional parameters") {
        val op = userResource.operations.filter { op => op.method == Method.Get && op.path == "/users/:guid" }.head
        val r = Play2Route(ssd, "user", service.models("user"), op, userResource)
        r.verb should be("GET")
        r.url should be("/users/:guid")
        r.method should be("controllers.Users.getByGuid")
        r.params.mkString(", ") should be("guid: _root_.java.util.UUID")
      }

      it("POST w/ default path, no parameters") {
        val op = userResource.operations.filter { op => op.method == Method.Post && op.path == "/users" }.head
        val r = Play2Route(ssd, "user", service.models("user"), op, userResource)
        r.verb should be("POST")
        r.url should be("/users")
        r.method should be("controllers.Users.post")
        r.params.mkString(", ") should be("")
      }

      it("PUT w/ guid in path, no parameters") {
        val op = userResource.operations.filter { op => op.method == Method.Put && op.path == "/users/:guid" }.head
        val r = Play2Route(ssd, "user", service.models("user"), op, userResource)
        r.verb should be("PUT")
        r.url should be("/users/:guid")
        r.method should be("controllers.Users.putByGuid")
        r.params.mkString(", ") should be("guid: _root_.java.util.UUID")
      }
    }

    describe("membership_request resource") {
      lazy val membershipRequestResource = getScalaResource(ssd, "membership_requests")

      it("POST /membership_requests/:guid/accept") {
        val op = membershipRequestResource.operations.filter { op => op.method == Method.Post && op.path == "/membership_requests/:guid/accept" }.head
        val r = Play2Route(ssd, "membership_request", service.models("membership_resource"), op, membershipRequestResource)
        r.verb should be("POST")
        r.url should be("/membership_requests/:guid/accept")
        r.method should be("controllers.MembershipRequests.postAcceptByGuid")
        r.params.mkString(", ") should be("guid: _root_.java.util.UUID")
      }
    }

    describe("service resource") {
      it("GET /:orgKey") {
        val membershipRequestResource = getScalaResource(ssd, "membership_request")
        val op = getScalaMethod(ssd, "service", Method.Get, "/:orgKey")
        val r = Play2Route(ssd, "membership_request", service.models("membership_request"), op, membershipRequestResource)
        r.method should be("controllers.Services.getByOrgKey")
      }
    }
  }

  describe("with reference-api service") {
    lazy val service = TestHelper.parseFile(s"reference-api/api.json")
    lazy val ssd = new ScalaService(service)

    it("normalizes explicit paths that match resource name") {
      val resource = getScalaResource(ssd, "organization")
      val op = getScalaMethod(ssd, "organization", Method.Get, "/organizations")
      val r = Play2Route(ssd, "organization", service.models("organization"), op, resource)
      r.method should be("controllers.Organizations.get")
    }

    it("enums are strongly typed") {
      val resource = getScalaResource(ssd, "user")
      val op = getScalaMethod(ssd, "user", Method.Get, "/users/:age_group")
      val r = Play2Route(ssd, "user", service.models("user"), op, resource)
      r.method should be("controllers.Users.getByAgeGroup")
      r.params.mkString("") should be("age_group: apidocreferenceapi.models.AgeGroup")
    }

    it("supports multiple query parameters") {
      val echoResource = getScalaResource(ssd, "echo")
      val op = getScalaMethod(ssd, "echo", Method.Get, "/echoes")
      val r = Play2Route(ssd, "echo", service.models("echo"), op, echoResource)
      r.method should be("controllers.Echoes.get")
      r.params.mkString(" ") should be("foo: scala.Option[String]")
      r.paramComments.getOrElse("") should be("""
# Additional parameters to GET /echoes
#   - optional_messages: scala.Option[Seq[String]]
#   - required_messages: Seq[String]
""".trim)

      TestHelper.assertEqualsFile(
        "test/resources/generators/play-2-route-reference-api.routes",
        Play2RouteGenerator(InvocationForm(service)).invoke().getOrElse("")
      )
    }

    it("camel cases hypen in route") {
      val echoResource = getScalaResource(ssd, "echo")
      val op = getScalaMethod(ssd, "echo", Method.Get, "/echoes/arrays-only")
      val r = Play2Route(ssd, "echo", service.models("echo"), op, echoResource)
      r.method should be("controllers.Echoes.getArraysOnly")
    }

  }

  describe("with quality service example") {

    lazy val quality = ScalaService(TestHelper.parseFile("test/resources/examples/quality.json"))

    it("correctly orders parameters defined in path and parameters") {
      val op = getScalaMethod(quality, "agenda_item", Method.Delete, "/meetings/:meeting_id/agenda_items/:id")
      op.parameters.map(_.name) should be(Seq("meeting_id", "id"))
      op.parameters.map(_.`type`) should be(Seq("long", "long"))
    }

  }

}