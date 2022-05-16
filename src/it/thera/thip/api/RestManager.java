package it.thera.thip.api;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Feature;
import javax.ws.rs.ext.ExceptionMapper;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;

import com.thera.thermfw.rs.ObjectsResource;
import com.thera.thermfw.rs.ServicesResource;
import com.thera.thermfw.rs.config.RestApplicationListener;
import com.thera.thermfw.rs.errors.CatchAllExceptionMapper;
import com.thera.thermfw.rs.errors.PantheraApiExceptionMapper;
import com.thera.thermfw.rs.security.AuthenticationFilter;
import com.thera.thermfw.rs.security.AuthenticationResource;

import it.thera.thip.base.pthupd.api.CatalogoFixApiResource;
import it.thera.thip.base.wpu.api.WpuApiResource;

/*
 * Revisions:
 * Fix     Date          Owner      Description
 * 35311   24/02/2022    PJ         Rilascio infrastruttura webservice su JAX-RS
 */

public class RestManager {

	public List<Class<? extends ExceptionMapper>> exceptionMappers() {
		List<Class<? extends ExceptionMapper>> mappers = new ArrayList<Class<? extends ExceptionMapper>>();
		mappers.add(PantheraApiExceptionMapper.class);
		mappers.add(CatchAllExceptionMapper.class);
		return mappers;
	}
	
	public List<Class<? extends ApplicationEventListener>> eventListeners() {
		List<Class<? extends ApplicationEventListener>> listeners = new ArrayList<Class<? extends ApplicationEventListener>>();
		listeners.add(RestApplicationListener.class);
		return listeners;
	}
	
	public List<Class<? extends ContainerRequestFilter>> filters() {
		List<Class<? extends ContainerRequestFilter>> filters = new ArrayList<Class<? extends ContainerRequestFilter>>();
		filters.add(AuthenticationFilter.class);
		return filters;
	}
	
	public List<Class<? extends Feature>> features() {
		List<Class<? extends Feature>> features = new ArrayList<Class<? extends Feature>>();
		features.add(MultiPartFeature.class);
		return features;
	}
	
	public Class[] resources() {
		Class[] resources = new Class[] {
			AuthenticationResource.class,
			ObjectsResource.class,
			ServicesResource.class,
			WpuApiResource.class,
			CatalogoFixApiResource.class
		};
		return resources;
	}
	
}
